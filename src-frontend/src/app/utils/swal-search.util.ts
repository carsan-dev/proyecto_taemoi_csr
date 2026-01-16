type SwalSelectSearchOptions = {
  selectId: string;
  inputId?: string;
  placeholder?: string;
  debounceMs?: number;
  autoFocus?: boolean;
};

export const attachSwalSelectSearch = (options: SwalSelectSearchOptions): void => {
  const select = document.getElementById(options.selectId) as HTMLSelectElement | null;
  if (!select || !select.parentElement) {
    return;
  }

  const parent = select.parentElement;
  if (parent.querySelector(`[data-swal-combobox-for="${options.selectId}"]`)) {
    return;
  }

  const wrapper = document.createElement('div');
  wrapper.className = 'swal2-select-combobox';
  wrapper.dataset['swalComboboxFor'] = options.selectId;

  const input = document.createElement('input');
  input.type = 'text';
  input.id = options.inputId ?? `${options.selectId}-input`;
  input.autocomplete = 'off';
  input.placeholder = options.placeholder ?? 'Buscar...';
  input.setAttribute('aria-label', 'Buscar opciones');
  input.setAttribute('role', 'combobox');
  input.setAttribute('aria-haspopup', 'listbox');
  input.className = 'swal2-input swal2-select-input';
  input.disabled = select.disabled;

  const listboxId = `${options.selectId}-listbox`;
  input.setAttribute('aria-controls', listboxId);
  input.setAttribute('aria-expanded', 'false');

  const dropdown = document.createElement('div');
  dropdown.className = 'swal2-select-dropdown';
  dropdown.id = listboxId;
  dropdown.setAttribute('role', 'listbox');

  wrapper.appendChild(input);
  wrapper.appendChild(dropdown);
  parent.insertBefore(wrapper, select);

  select.style.display = 'none';
  const label = parent.querySelector(`label[for="${options.selectId}"]`) as HTMLLabelElement | null;
  if (label) {
    label.setAttribute('for', input.id);
  }

  const normalize = (value: string): string =>
    value
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .trim();

  const getPlaceholderText = (): string => {
    const placeholderOption = Array.from(select.options).find(
      (option) => option.disabled && !option.value
    );
    return (placeholderOption?.textContent ?? '').trim();
  };

  const optionEls: HTMLDivElement[] = [];
  let emptyEl: HTMLDivElement | null = null;
  let activeIndex = -1;
  let isOpen = false;
  let debounceHandle: number | undefined;
  const debounceMs = options.debounceMs ?? 250;

  const syncSelectedClasses = (): void => {
    optionEls.forEach((optionEl, index) => {
      const option = select.options[index];
      if (!option) {
        return;
      }
      if (option.selected) {
        optionEl.classList.add('is-selected');
        optionEl.setAttribute('aria-selected', 'true');
      } else {
        optionEl.classList.remove('is-selected');
        optionEl.setAttribute('aria-selected', 'false');
      }
    });
  };

  const syncFromSelect = (): void => {
    const selected = select.selectedOptions[0] ?? select.options[select.selectedIndex];
    if (selected && !(selected.disabled && !selected.value)) {
      input.value = (selected.textContent ?? '').trim();
    } else {
      input.value = '';
      input.placeholder = getPlaceholderText() || (options.placeholder ?? 'Buscar...');
    }
    syncSelectedClasses();
  };

  const clearActive = (): void => {
    optionEls.forEach((optionEl) => optionEl.classList.remove('is-active'));
    activeIndex = -1;
  };

  const setActiveIndex = (index: number): void => {
    clearActive();
    activeIndex = index;
    const optionEl = optionEls[index];
    if (optionEl) {
      optionEl.classList.add('is-active');
      optionEl.scrollIntoView({ block: 'nearest' });
    }
  };

  const getNavigableIndices = (): number[] =>
    optionEls
      .map((optionEl, index) => ({ optionEl, index }))
      .filter(({ optionEl, index }) => optionEl.dataset['visible'] !== 'false' && !select.options[index]?.disabled)
      .map(({ index }) => index);

  const setActiveToFirstVisible = (): void => {
    const navigable = getNavigableIndices();
    if (navigable.length === 0) {
      clearActive();
      return;
    }
    setActiveIndex(navigable[0]);
  };

  const setActiveToSelected = (): void => {
    const selectedIndex = select.selectedIndex;
    if (selectedIndex >= 0) {
      setActiveIndex(selectedIndex);
    } else {
      setActiveToFirstVisible();
    }
  };

  const filterOptions = (): void => {
    const term = normalize(input.value);
    let visibleCount = 0;

    optionEls.forEach((optionEl, index) => {
      const option = select.options[index];
      if (!option) {
        return;
      }
      const optionText = normalize(option.textContent ?? '');
      const isPlaceholder = option.disabled && !option.value;
      let visible = !term || optionText.includes(term);
      if (term && isPlaceholder) {
        visible = false;
      }
      optionEl.dataset['visible'] = visible ? 'true' : 'false';
      optionEl.style.display = visible ? '' : 'none';
      if (visible && !option.disabled) {
        visibleCount += 1;
      }
    });

    if (emptyEl) {
      emptyEl.style.display = visibleCount === 0 ? '' : 'none';
    }

    if (term) {
      setActiveToFirstVisible();
    } else {
      setActiveToSelected();
    }
  };

  const openDropdown = (resetActive: boolean, applyFilter: boolean = true): void => {
    isOpen = true;
    dropdown.classList.add('is-open');
    input.setAttribute('aria-expanded', 'true');
    if (applyFilter) {
      filterOptions();
    }
    if (resetActive && applyFilter) {
      setActiveToSelected();
    }
  };

  const closeDropdown = (restoreValue: boolean): void => {
    if (!isOpen) {
      if (restoreValue) {
        syncFromSelect();
      }
      return;
    }
    isOpen = false;
    dropdown.classList.remove('is-open');
    input.setAttribute('aria-expanded', 'false');
    if (restoreValue) {
      syncFromSelect();
    }
  };

  const selectOption = (index: number): void => {
    const option = select.options[index];
    if (!option || option.disabled) {
      return;
    }
    select.selectedIndex = index;
    select.dispatchEvent(new Event('change', { bubbles: true }));
    syncFromSelect();
    closeDropdown(true);
  };

  const moveActive = (direction: number): void => {
    const navigable = getNavigableIndices();
    if (navigable.length === 0) {
      clearActive();
      return;
    }
    const currentPos = navigable.indexOf(activeIndex);
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
    setActiveIndex(navigable[nextPos]);
  };

  const handleKeydown = (event: KeyboardEvent): void => {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        openDropdown(false);
        moveActive(1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        openDropdown(false);
        moveActive(-1);
        break;
      case 'Enter':
        if (isOpen) {
          event.preventDefault();
          if (activeIndex >= 0) {
            selectOption(activeIndex);
          }
        }
        break;
      case 'Escape':
        closeDropdown(true);
        break;
      case 'Tab':
        closeDropdown(true);
        break;
      default:
        break;
    }
  };

  const scheduleFilter = (): void => {
    openDropdown(false, false);
    if (debounceHandle) {
      window.clearTimeout(debounceHandle);
    }
    debounceHandle = window.setTimeout(filterOptions, debounceMs);
  };

  const buildOptions = (): void => {
    dropdown.innerHTML = '';
    optionEls.length = 0;

    Array.from(select.options).forEach((option, index) => {
      const optionEl = document.createElement('div');
      optionEl.textContent = option.textContent ?? '';
      optionEl.dataset['index'] = String(index);
      optionEl.dataset['visible'] = 'true';
      optionEl.className = 'swal2-select-option';
      optionEl.setAttribute('role', 'option');
      if (option.disabled) {
        optionEl.classList.add('is-disabled');
        optionEl.setAttribute('aria-disabled', 'true');
      }
      optionEl.addEventListener('mousedown', (event) => {
        event.preventDefault();
        if (!option.disabled) {
          selectOption(index);
        }
      });
      dropdown.appendChild(optionEl);
      optionEls.push(optionEl);
    });

    emptyEl = document.createElement('div');
    emptyEl.className = 'swal2-select-empty';
    emptyEl.textContent = 'Sin resultados';
    dropdown.appendChild(emptyEl);
  };

  buildOptions();
  syncFromSelect();
  filterOptions();

  input.addEventListener('focus', () => {
    if (!select.disabled) {
      openDropdown(true);
      window.setTimeout(() => input.select(), 0);
    }
  });
  input.addEventListener('click', () => {
    if (!select.disabled) {
      openDropdown(false);
    }
  });
  input.addEventListener('input', scheduleFilter);
  input.addEventListener('keydown', handleKeydown);
  input.addEventListener('blur', () => {
    window.setTimeout(() => closeDropdown(true), 150);
  });
  select.addEventListener('change', syncFromSelect);

  if (options.autoFocus !== false) {
    input.focus();
  }
};
