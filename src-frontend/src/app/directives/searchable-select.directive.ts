import { AfterViewInit, Directive, ElementRef, Input, NgZone, OnDestroy, Renderer2 } from '@angular/core';
import { Subscription } from 'rxjs';

@Directive({
  selector: 'select[appSearchableSelect]',
  standalone: true,
})
export class SearchableSelectDirective implements AfterViewInit, OnDestroy {
  @Input() searchPlaceholder = 'Buscar...';
  @Input() debounceMs = 250;

  private static nextId = 0;

  private wrapperEl?: HTMLDivElement;
  private inputEl?: HTMLInputElement;
  private dropdownEl?: HTMLDivElement;
  private emptyEl?: HTMLDivElement;
  private optionEls: HTMLDivElement[] = [];
  private activeIndex = -1;
  private isOpen = false;
  private debounceHandle?: number;
  private stableSub?: Subscription;
  private lastSelectValue?: string;
  private observer?: MutationObserver;
  private detachListeners: Array<() => void> = [];
  private labelEl?: HTMLLabelElement;
  private labelForBackup?: string;
  private fieldEl?: HTMLElement;
  private cardEl?: HTMLElement;
  private detachOpenListeners: Array<() => void> = [];
  private positionRafId?: number;

  constructor(
    private readonly elRef: ElementRef<HTMLSelectElement>,
    private readonly renderer: Renderer2,
    private readonly zone: NgZone
  ) {}

  ngAfterViewInit(): void {
    this.initCombobox();
  }

  ngOnDestroy(): void {
    if (this.debounceHandle) {
      window.clearTimeout(this.debounceHandle);
    }
    if (this.stableSub) {
      this.stableSub.unsubscribe();
    }
    this.detachListeners.forEach((detach) => detach());
    this.stopDropdownPositionTracking();
    if (this.positionRafId !== undefined) {
      window.cancelAnimationFrame(this.positionRafId);
      this.positionRafId = undefined;
    }
    if (this.observer) {
      this.observer.disconnect();
    }
    if (this.fieldEl) {
      this.renderer.removeClass(this.fieldEl, 'admin-select-field-open');
    }
    if (this.cardEl) {
      this.renderer.removeClass(this.cardEl, 'admin-select-card-open');
    }
    if (this.inputEl) {
      this.inputEl.remove();
    }
    if (this.dropdownEl) {
      this.dropdownEl.remove();
    }
    const select = this.elRef.nativeElement;
    this.renderer.removeStyle(select, 'position');
    this.renderer.removeStyle(select, 'opacity');
    this.renderer.removeStyle(select, 'pointer-events');
    this.renderer.removeStyle(select, 'height');
    this.renderer.removeStyle(select, 'width');
    if (this.labelEl && this.labelForBackup !== undefined) {
      this.renderer.setAttribute(this.labelEl, 'for', this.labelForBackup);
    }
  }

  private initCombobox(): void {
    const select = this.elRef.nativeElement;
    if (!select.parentElement) {
      return;
    }
    if (select.dataset['searchableSelectInitialized'] === 'true') {
      return;
    }
    select.dataset['searchableSelectInitialized'] = 'true';

    this.wrapperEl = this.renderer.createElement('div') as HTMLDivElement;
    this.renderer.addClass(this.wrapperEl, 'admin-select-combobox');

    this.inputEl = this.renderer.createElement('input') as HTMLInputElement;
    this.renderer.setAttribute(this.inputEl, 'type', 'text');
    this.renderer.setAttribute(this.inputEl, 'autocomplete', 'off');
    this.renderer.setAttribute(this.inputEl, 'inputmode', 'search');
    this.renderer.setAttribute(this.inputEl, 'role', 'combobox');
    this.renderer.setAttribute(this.inputEl, 'aria-haspopup', 'listbox');
    this.renderer.setAttribute(this.inputEl, 'aria-expanded', 'false');
    this.renderer.addClass(this.inputEl, 'admin-form-control');
    this.renderer.addClass(this.inputEl, 'admin-select-input');

    if (select.classList.contains('form-select-sm') || select.classList.contains('form-control-sm')) {
      this.renderer.addClass(this.inputEl, 'admin-select-input-sm');
    }

    const listboxId = this.getListboxId(select);
    this.renderer.setAttribute(this.inputEl, 'aria-controls', listboxId);
    this.renderer.setAttribute(this.inputEl, 'aria-label', 'Buscar opciones');

    this.dropdownEl = this.renderer.createElement('div') as HTMLDivElement;
    this.renderer.addClass(this.dropdownEl, 'admin-select-dropdown');
    this.renderer.addClass(this.dropdownEl, 'is-floating');
    this.renderer.setAttribute(this.dropdownEl, 'id', listboxId);
    this.renderer.setAttribute(this.dropdownEl, 'role', 'listbox');

    const parent = select.parentElement;
    this.renderer.insertBefore(parent, this.wrapperEl, select);
    this.renderer.appendChild(this.wrapperEl, this.inputEl);
    this.renderer.appendChild(document.body, this.dropdownEl);
    this.renderer.appendChild(this.wrapperEl, select);

    this.hideSelect(select);
    this.bindLabel(select);
    this.fieldEl = (this.wrapperEl.closest('.admin-form-field, .form-group') as HTMLElement | null) ?? undefined;
    this.cardEl = (
      this.wrapperEl.closest('.grupo-card, .admin-card, .info-card') as HTMLElement | null
    ) ?? undefined;
    this.syncDisabled();
    this.buildOptions();
    this.syncFromSelect();
    this.bindEvents();
    this.observeChanges(select);
    this.watchSelectValue(select);
  }

  private hideSelect(select: HTMLSelectElement): void {
    this.renderer.setStyle(select, 'position', 'absolute');
    this.renderer.setStyle(select, 'opacity', '0');
    this.renderer.setStyle(select, 'pointer-events', 'none');
    this.renderer.setStyle(select, 'height', '0');
    this.renderer.setStyle(select, 'width', '0');
  }

  private bindLabel(select: HTMLSelectElement): void {
    const selectId = select.getAttribute('id');
    if (!selectId || !this.wrapperEl || !this.inputEl) {
      return;
    }
    const inputId = `${selectId}-input`;
    this.renderer.setAttribute(this.inputEl, 'id', inputId);
    const root = this.wrapperEl.parentElement || select.parentElement;
    if (!root) {
      return;
    }
    const label = root.querySelector(`label[for="${selectId}"]`) as HTMLLabelElement | null;
    if (!label) {
      return;
    }
    this.labelEl = label;
    this.labelForBackup = label.getAttribute('for') ?? undefined;
    this.renderer.setAttribute(label, 'for', inputId);
  }

  private bindEvents(): void {
    if (!this.inputEl || !this.wrapperEl) {
      return;
    }
    const select = this.elRef.nativeElement;

    this.detachListeners.push(
      this.renderer.listen(this.inputEl, 'focus', () => {
        if (select.disabled) {
          return;
        }
        this.openDropdown(true);
        window.setTimeout(() => {
          this.inputEl?.select();
        }, 0);
      })
    );

    this.detachListeners.push(
      this.renderer.listen(this.inputEl, 'click', () => {
        if (select.disabled) {
          return;
        }
        this.openDropdown(false);
      })
    );

    this.detachListeners.push(
      this.renderer.listen(this.inputEl, 'input', () => {
        this.scheduleFilter();
      })
    );

    this.detachListeners.push(
      this.renderer.listen(this.inputEl, 'keydown', (event: KeyboardEvent) => {
        this.handleKeydown(event);
      })
    );

    this.detachListeners.push(
      this.renderer.listen(this.inputEl, 'blur', () => {
        select.dispatchEvent(new Event('blur', { bubbles: true }));
        window.setTimeout(() => {
          this.closeDropdown(true);
        }, 150);
      })
    );

    this.detachListeners.push(
      this.renderer.listen(select, 'change', () => {
        this.syncFromSelect();
      })
    );

    this.detachListeners.push(
      this.renderer.listen('document', 'click', (event: Event) => {
        if (!this.wrapperEl || !this.isOpen) {
          return;
        }
        const target = event.target as Node;
        if (this.wrapperEl.contains(target)) {
          return;
        }
        if (this.dropdownEl && this.dropdownEl.contains(target)) {
          return;
        }
        this.closeDropdown(true);
      })
    );
  }

  private observeChanges(select: HTMLSelectElement): void {
    this.zone.runOutsideAngular(() => {
      this.observer = new MutationObserver((mutations) => {
        let shouldRebuild = false;
        let shouldSyncDisabled = false;

        for (const mutation of mutations) {
          if (mutation.type === 'childList') {
            shouldRebuild = true;
          }
          if (mutation.type === 'attributes' && mutation.attributeName === 'disabled') {
            shouldSyncDisabled = true;
          }
        }

        if (shouldSyncDisabled) {
          this.syncDisabled();
        }

        if (shouldRebuild) {
          this.buildOptions();
          this.syncFromSelect();
        }
      });

      this.observer.observe(select, {
        childList: true,
        subtree: true,
        attributes: true,
        attributeFilter: ['disabled'],
      });
    });
  }

  private watchSelectValue(select: HTMLSelectElement): void {
    this.lastSelectValue = select.value;
    this.stableSub = this.zone.onStable.subscribe(() => {
      if (!this.inputEl) {
        return;
      }
      if (this.isOpen || document.activeElement === this.inputEl) {
        return;
      }
      if (select.value !== this.lastSelectValue) {
        this.syncFromSelect();
      }
    });
  }

  private buildOptions(): void {
    if (!this.dropdownEl) {
      return;
    }
    const select = this.elRef.nativeElement;
    this.dropdownEl.innerHTML = '';
    this.optionEls = [];

    Array.from(select.options).forEach((option, index) => {
      const optionEl = this.renderer.createElement('div') as HTMLDivElement;
      optionEl.textContent = option.textContent ?? '';
      optionEl.dataset['index'] = String(index);
      optionEl.dataset['visible'] = 'true';
      this.renderer.addClass(optionEl, 'admin-select-option');
      this.renderer.setAttribute(optionEl, 'role', 'option');

      if (option.disabled) {
        this.renderer.addClass(optionEl, 'is-disabled');
        this.renderer.setAttribute(optionEl, 'aria-disabled', 'true');
      }

      this.detachListeners.push(
        this.renderer.listen(optionEl, 'mousedown', (event: MouseEvent) => {
          event.preventDefault();
          if (option.disabled) {
            return;
          }
          this.selectOption(index);
        })
      );

      this.renderer.appendChild(this.dropdownEl, optionEl);
      this.optionEls.push(optionEl);
    });

    this.emptyEl = this.renderer.createElement('div') as HTMLDivElement;
    this.renderer.addClass(this.emptyEl, 'admin-select-empty');
    this.renderer.setProperty(this.emptyEl, 'textContent', 'Sin resultados');
    this.renderer.appendChild(this.dropdownEl, this.emptyEl);
    this.syncSelectedClasses();
  }

  private syncFromSelect(): void {
    if (!this.inputEl) {
      return;
    }
    const select = this.elRef.nativeElement;
    const selected = select.selectedOptions[0] ?? select.options[select.selectedIndex];
    const placeholderText = this.getPlaceholderText(select);

    if (selected && !(selected.disabled && !selected.value)) {
      this.inputEl.value = (selected.textContent ?? '').trim();
    } else {
      this.inputEl.value = '';
      this.inputEl.placeholder = placeholderText || this.searchPlaceholder;
    }

    this.syncSelectedClasses();
    this.lastSelectValue = select.value;
  }

  private syncSelectedClasses(): void {
    const select = this.elRef.nativeElement;
    this.optionEls.forEach((optionEl, index) => {
      const option = select.options[index];
      if (!option) {
        return;
      }
      if (option.selected) {
        this.renderer.addClass(optionEl, 'is-selected');
        this.renderer.setAttribute(optionEl, 'aria-selected', 'true');
      } else {
        this.renderer.removeClass(optionEl, 'is-selected');
        this.renderer.setAttribute(optionEl, 'aria-selected', 'false');
      }
    });
  }

  private scheduleFilter(): void {
    if (!this.inputEl) {
      return;
    }
    this.openDropdown(false, false);
    if (this.debounceHandle) {
      window.clearTimeout(this.debounceHandle);
    }
    this.debounceHandle = window.setTimeout(() => {
      this.filterOptions();
    }, this.debounceMs);
  }

  private filterOptions(): void {
    if (!this.inputEl || !this.dropdownEl) {
      return;
    }
    const term = this.normalize(this.inputEl.value);
    const select = this.elRef.nativeElement;
    let visibleCount = 0;

    this.optionEls.forEach((optionEl, index) => {
      const option = select.options[index];
      if (!option) {
        return;
      }
      const optionText = this.normalize(option.textContent ?? '');
      const isPlaceholder = option.disabled && !option.value;
      let visible = !term || optionText.includes(term);
      if (term && isPlaceholder) {
        visible = false;
      }
      optionEl.dataset['visible'] = visible ? 'true' : 'false';
      this.renderer.setStyle(optionEl, 'display', visible ? '' : 'none');
      if (visible && !option.disabled) {
        visibleCount += 1;
      }
    });

    if (this.emptyEl) {
      this.renderer.setStyle(this.emptyEl, 'display', visibleCount === 0 ? '' : 'none');
    }

    if (term) {
      this.setActiveToFirstVisible();
    } else {
      this.setActiveToSelected();
    }

  }

  private handleKeydown(event: KeyboardEvent): void {
    if (!this.inputEl) {
      return;
    }
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.openDropdown(false);
        this.moveActive(1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.openDropdown(false);
        this.moveActive(-1);
        break;
      case 'Enter':
        if (this.isOpen) {
          event.preventDefault();
          this.selectActive();
        }
        break;
      case 'Escape':
        this.closeDropdown(true);
        break;
      case 'Tab':
        this.closeDropdown(true);
        break;
      default:
        break;
    }
  }

  private moveActive(direction: number): void {
    const navigable = this.getNavigableIndices();
    if (navigable.length === 0) {
      this.clearActive();
      return;
    }
    const currentIndex = this.activeIndex;
    const currentPos = navigable.indexOf(currentIndex);
    let nextPos = currentPos + direction;
    if (currentPos === -1) {
      nextPos = direction > 0 ? 0 : navigable.length - 1;
    }
    if (nextPos < 0) {
      nextPos = navigable.length - 1;
    }
    if (nextPos >= navigable.length) {
      nextPos = 0;
    }
    this.setActiveIndex(navigable[nextPos]);
  }

  private setActiveToFirstVisible(): void {
    const navigable = this.getNavigableIndices();
    if (navigable.length === 0) {
      this.clearActive();
      return;
    }
    this.setActiveIndex(navigable[0]);
  }

  private setActiveToSelected(): void {
    const select = this.elRef.nativeElement;
    const selectedIndex = select.selectedIndex;
    if (selectedIndex >= 0) {
      this.setActiveIndex(selectedIndex);
    } else {
      this.setActiveToFirstVisible();
    }
  }

  private getNavigableIndices(): number[] {
    const select = this.elRef.nativeElement;
    return this.optionEls
      .map((optionEl, index) => ({ optionEl, index }))
      .filter(({ optionEl, index }) => optionEl.dataset['visible'] !== 'false' && !select.options[index]?.disabled)
      .map(({ index }) => index);
  }

  private setActiveIndex(index: number): void {
    this.clearActive();
    this.activeIndex = index;
    const optionEl = this.optionEls[index];
    if (optionEl) {
      this.renderer.addClass(optionEl, 'is-active');
      optionEl.scrollIntoView({ block: 'nearest' });
    }
  }

  private clearActive(): void {
    this.optionEls.forEach((optionEl) => {
      this.renderer.removeClass(optionEl, 'is-active');
    });
    this.activeIndex = -1;
  }

  private selectActive(): void {
    if (this.activeIndex < 0) {
      return;
    }
    this.selectOption(this.activeIndex);
  }

  private selectOption(index: number): void {
    const select = this.elRef.nativeElement;
    if (!select.options[index] || select.options[index].disabled) {
      return;
    }
    select.selectedIndex = index;
    select.dispatchEvent(new Event('change', { bubbles: true }));
    this.syncFromSelect();
    this.closeDropdown(true);
  }

  private openDropdown(resetActive: boolean, applyFilter: boolean = true): void {
    if (!this.dropdownEl || !this.inputEl || !this.wrapperEl) {
      return;
    }

    const wasClosed = !this.isOpen;
    this.isOpen = true;
    this.renderer.addClass(this.dropdownEl, 'is-open');
    this.renderer.addClass(this.wrapperEl, 'is-open');
    if (this.fieldEl) {
      this.renderer.addClass(this.fieldEl, 'admin-select-field-open');
    }
    if (this.cardEl) {
      this.renderer.addClass(this.cardEl, 'admin-select-card-open');
    }
    this.renderer.setAttribute(this.inputEl, 'aria-expanded', 'true');
    if (applyFilter) {
      this.filterOptions();
    }
    if (resetActive && applyFilter) {
      this.setActiveToSelected();
    }
    if (wasClosed) {
      this.startDropdownPositionTracking();
    }
    this.scheduleDropdownPositionUpdate();
  }

  private closeDropdown(restoreValue: boolean): void {
    if (!this.dropdownEl || !this.inputEl) {
      return;
    }
    if (!this.isOpen) {
      this.stopDropdownPositionTracking();
      if (this.positionRafId !== undefined) {
        window.cancelAnimationFrame(this.positionRafId);
        this.positionRafId = undefined;
      }
      if (this.fieldEl) {
        this.renderer.removeClass(this.fieldEl, 'admin-select-field-open');
      }
      if (this.cardEl) {
        this.renderer.removeClass(this.cardEl, 'admin-select-card-open');
      }
      if (restoreValue) {
        this.syncFromSelect();
      }
      return;
    }
    this.isOpen = false;
    this.stopDropdownPositionTracking();
    if (this.positionRafId !== undefined) {
      window.cancelAnimationFrame(this.positionRafId);
      this.positionRafId = undefined;
    }
    this.renderer.removeClass(this.dropdownEl, 'is-open');
    this.renderer.removeClass(this.dropdownEl, 'is-open-up');
    this.renderer.removeClass(this.wrapperEl, 'is-open');
    if (this.fieldEl) {
      this.renderer.removeClass(this.fieldEl, 'admin-select-field-open');
    }
    if (this.cardEl) {
      this.renderer.removeClass(this.cardEl, 'admin-select-card-open');
    }
    this.renderer.setAttribute(this.inputEl, 'aria-expanded', 'false');
    if (restoreValue) {
      this.syncFromSelect();
    }
  }

  private syncDisabled(): void {
    const select = this.elRef.nativeElement;
    if (this.inputEl) {
      this.renderer.setProperty(this.inputEl, 'disabled', select.disabled);
    }
  }

  private getPlaceholderText(select: HTMLSelectElement): string {
    const placeholderOption = Array.from(select.options).find(
      (option) => option.disabled && !option.value
    );
    return (placeholderOption?.textContent ?? '').trim();
  }

  private getListboxId(select: HTMLSelectElement): string {
    SearchableSelectDirective.nextId += 1;
    const base = select.getAttribute('id') || 'select';
    return `${base}-listbox-${SearchableSelectDirective.nextId}`;
  }

  private normalize(value: string): string {
    return value
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .trim();
  }

  private startDropdownPositionTracking(): void {
    if (this.detachOpenListeners.length > 0) {
      return;
    }

    const onViewportChange = () => {
      if (!this.isOpen) {
        return;
      }
      this.scheduleDropdownPositionUpdate();
    };

    const addListener = (
      target: EventTarget,
      eventName: string,
      options?: boolean | AddEventListenerOptions
    ): void => {
      target.addEventListener(eventName, onViewportChange, options);
      this.detachOpenListeners.push(() => {
        target.removeEventListener(eventName, onViewportChange, options);
      });
    };

    addListener(window, 'resize', { passive: true });
    addListener(window, 'orientationchange');
    addListener(document, 'scroll', true);
  }

  private stopDropdownPositionTracking(): void {
    this.detachOpenListeners.forEach((detach) => detach());
    this.detachOpenListeners = [];
  }

  private scheduleDropdownPositionUpdate(): void {
    if (this.positionRafId !== undefined) {
      window.cancelAnimationFrame(this.positionRafId);
    }
    this.positionRafId = window.requestAnimationFrame(() => {
      this.positionRafId = undefined;
      this.updateDropdownPosition();
    });
  }

  private updateDropdownPosition(): void {
    if (!this.wrapperEl || !this.dropdownEl || !this.inputEl || !this.isOpen) {
      return;
    }

    const gap = 4;
    const viewportPadding = 8;
    const minimumDropdownHeight = 120;
    const desiredMaxHeight = 240;
    const inputRect = this.inputEl.getBoundingClientRect();
    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;
    const topBoundary = this.getHeaderBottomBoundary(viewportPadding, gap);

    const width = Math.min(inputRect.width, viewportWidth - viewportPadding * 2);
    let left = inputRect.left;
    if (left + width > viewportWidth - viewportPadding) {
      left = viewportWidth - viewportPadding - width;
    }
    if (left < viewportPadding) {
      left = viewportPadding;
    }

    const availableBelow = Math.max(0, viewportHeight - inputRect.bottom - viewportPadding);
    const availableAbove = Math.max(0, inputRect.top - topBoundary);
    const requiredHeight = Math.min(desiredMaxHeight, Math.max(minimumDropdownHeight, this.dropdownEl.scrollHeight || 0));
    const openUp = (
      availableBelow < requiredHeight &&
      availableAbove > availableBelow &&
      availableAbove >= minimumDropdownHeight
    );
    const maxHeight = Math.max(
      minimumDropdownHeight,
      Math.min(desiredMaxHeight, openUp ? availableAbove - gap : availableBelow - gap)
    );

    let top = inputRect.bottom + gap;
    if (openUp) {
      top = Math.max(topBoundary, inputRect.top - gap - maxHeight);
      this.renderer.addClass(this.dropdownEl, 'is-open-up');
    } else {
      top = Math.max(topBoundary, inputRect.bottom + gap);
      this.renderer.removeClass(this.dropdownEl, 'is-open-up');
    }

    this.renderer.setStyle(this.dropdownEl, 'position', 'fixed');
    this.renderer.setStyle(this.dropdownEl, 'left', `${Math.round(left)}px`);
    this.renderer.setStyle(this.dropdownEl, 'top', `${Math.round(top)}px`);
    this.renderer.setStyle(this.dropdownEl, 'width', `${Math.round(width)}px`);
    this.renderer.setStyle(this.dropdownEl, 'max-height', `${Math.round(maxHeight)}px`);
    // Keep floating dropdown above component modals (typically z-index: 1050).
    this.renderer.setStyle(this.dropdownEl, 'z-index', '1080');
  }

  private getHeaderBottomBoundary(viewportPadding: number, gap: number): number {
    const selectors = [
      '.header-anonimo.fixed-header',
      '.header-user.fixed-header',
      '.admin-top-navbar',
      'header.fixed-header',
      '.navbar.fixed-top',
      '.navbar.sticky-top',
    ];

    let boundaryTop = viewportPadding;
    for (const selector of selectors) {
      const elements = document.querySelectorAll<HTMLElement>(selector);
      elements.forEach((element) => {
        const styles = window.getComputedStyle(element);
        if (styles.display === 'none' || styles.visibility === 'hidden') {
          return;
        }
        if (styles.position !== 'fixed' && styles.position !== 'sticky') {
          return;
        }
        const rect = element.getBoundingClientRect();
        if (rect.bottom <= 0 || rect.top >= window.innerHeight) {
          return;
        }
        boundaryTop = Math.max(boundaryTop, Math.round(rect.bottom + gap));
      });
    }

    return boundaryTop;
  }
}
