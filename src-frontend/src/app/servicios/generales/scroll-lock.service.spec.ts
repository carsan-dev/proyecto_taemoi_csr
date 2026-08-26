import { ScrollLockService } from './scroll-lock.service';

describe('ScrollLockService', () => {
  let service: ScrollLockService;

  beforeEach(() => {
    document.body.removeAttribute('style');
    document.documentElement.removeAttribute('style');
    service = new ScrollLockService(document);
  });

  afterEach(() => service.ngOnDestroy());

  it('keeps the viewport locked until every nested owner releases it', () => {
    spyOnProperty(window, 'scrollY', 'get').and.returnValue(420);
    const windowScroll = spyOn(window, 'scrollTo') as jasmine.Spy;
    const releaseMenu = service.lock();
    const releaseModal = service.lock();

    expect(service.activeLocks).toBe(2);
    expect(document.body.style.position).toBe('fixed');
    expect(document.body.style.top).toBe('-420px');

    releaseModal();
    expect(service.activeLocks).toBe(1);
    expect(document.body.style.position).toBe('fixed');

    releaseMenu();
    expect(service.activeLocks).toBe(0);
    expect(document.body.style.position).toBe('');
    expect(windowScroll).toHaveBeenCalledOnceWith({ left: window.scrollX, top: 420, behavior: 'auto' });
  });

  it('makes release functions idempotent', () => {
    const release = service.lock();
    release();
    release();

    expect(service.activeLocks).toBe(0);
  });

  it('does not overwrite route scroll when an overlay closes after navigation', () => {
    const originalUrl = `${location.pathname}${location.search}${location.hash}`;
    const windowScroll = spyOn(window, 'scrollTo');
    history.replaceState(null, '', `${location.pathname}?overlay=before`);
    const release = service.lock();

    history.replaceState(null, '', `${location.pathname}?overlay=after`);
    release();

    expect(windowScroll).not.toHaveBeenCalled();
    history.replaceState(null, '', originalUrl);
  });
});
