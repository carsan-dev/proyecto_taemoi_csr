import {
  NavigationEnd,
  NavigationStart,
  Router,
  Scroll,
} from '@angular/router';
import { Subject } from 'rxjs';

import { PageScrollService, PageScrollSnapshot } from './page-scroll.service';
import { RouteScrollCoordinator } from './route-scroll-coordinator.service';

describe('RouteScrollCoordinator', () => {
  let events: Subject<unknown>;
  let router: Router;
  let pageScroll: jasmine.SpyObj<PageScrollService>;
  let coordinator: RouteScrollCoordinator;
  const outgoing: PageScrollSnapshot = { position: [0, 640] };

  beforeEach(() => {
    events = new Subject();
    router = {
      url: '/listado?page=1',
      events,
      routerState: { snapshot: { root: { data: {}, firstChild: null } } },
    } as unknown as Router;
    pageScroll = jasmine.createSpyObj<PageScrollService>('PageScrollService', [
      'setManualHistoryRestoration',
      'cancelPendingRestoration',
      'captureSnapshot',
      'restoreSnapshotAfterNextRender',
      'scrollToTopAfterNextRender',
      'scrollToAnchorAfterNextRender',
    ]);
    pageScroll.captureSnapshot.and.returnValue(outgoing);
    pageScroll.restoreSnapshotAfterNextRender.and.returnValue(Promise.resolve(true));
    pageScroll.scrollToTopAfterNextRender.and.returnValue(Promise.resolve(true));
    pageScroll.scrollToAnchorAfterNextRender.and.returnValue(Promise.resolve(true));
    coordinator = new RouteScrollCoordinator(router, pageScroll);
    coordinator.start();
  });

  afterEach(() => coordinator.ngOnDestroy());

  it('moves a new route to the top', () => {
    events.next(new NavigationStart(1, '/contacto'));
    const end = new NavigationEnd(1, '/contacto', '/contacto');
    events.next(end);
    events.next(new Scroll(end, null, null));

    expect(pageScroll.scrollToTopAfterNextRender).toHaveBeenCalledOnceWith('auto');
  });

  it('preserves position for query-param-only navigation', () => {
    events.next(new NavigationStart(1, '/listado?page=2'));
    const end = new NavigationEnd(1, '/listado?page=2', '/listado?page=2');
    events.next(end);
    events.next(new Scroll(end, null, null));

    expect(pageScroll.restoreSnapshotAfterNextRender).toHaveBeenCalledOnceWith(outgoing);
  });

  it('uses Angular history coordinates when no stable snapshot is available', () => {
    events.next(new NavigationStart(1, '/detalle'));
    const end = new NavigationEnd(1, '/detalle', '/detalle');
    events.next(end);
    events.next(new Scroll(end, [10, 720], null));

    expect(pageScroll.restoreSnapshotAfterNextRender).toHaveBeenCalledOnceWith({ position: [10, 720] });
  });

  it('gives fragments priority and delegates fixed-header handling', () => {
    events.next(new NavigationStart(1, '/contacto#mapa'));
    const end = new NavigationEnd(1, '/contacto#mapa', '/contacto#mapa');
    events.next(end);
    events.next(new Scroll(end, null, 'mapa'));

    expect(pageScroll.scrollToAnchorAfterNextRender).toHaveBeenCalledOnceWith('mapa', 'smooth');
  });

  it('cancels a pending restoration as soon as another navigation starts', () => {
    events.next(new NavigationStart(1, '/uno'));
    events.next(new NavigationStart(2, '/dos'));

    expect(pageScroll.cancelPendingRestoration).toHaveBeenCalledTimes(2);
  });

  it('ignores a stale Scroll event after a newer navigation has started', () => {
    events.next(new NavigationStart(1, '/uno'));
    const firstEnd = new NavigationEnd(1, '/uno', '/uno');
    events.next(firstEnd);
    events.next(new NavigationStart(2, '/dos'));
    events.next(new Scroll(firstEnd, [0, 900], null));

    const secondEnd = new NavigationEnd(2, '/dos', '/dos');
    events.next(secondEnd);
    events.next(new Scroll(secondEnd, null, null));

    expect(pageScroll.restoreSnapshotAfterNextRender).not.toHaveBeenCalled();
    expect(pageScroll.scrollToTopAfterNextRender).toHaveBeenCalledOnceWith('auto');
  });
});
