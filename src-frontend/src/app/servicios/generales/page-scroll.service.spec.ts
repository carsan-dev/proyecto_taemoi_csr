import { PageScrollService, PageScrollSnapshot } from './page-scroll.service';

describe('PageScrollService', () => {
  let service: PageScrollService;
  let windowScroll: jasmine.Spy;

  beforeEach(() => {
    service = new PageScrollService(document);
    windowScroll = spyOn(window, 'scrollTo');
  });

  afterEach(() => {
    document.querySelectorAll('[data-page-scroll-test]').forEach((element) => element.remove());
  });

  it('captures a stable anchor together with an absolute fallback', () => {
    const anchor = document.createElement('section');
    anchor.id = 'personal';
    anchor.dataset['pageScrollTest'] = '';
    document.body.appendChild(anchor);
    spyOn(anchor, 'getBoundingClientRect').and.returnValue({ top: 140 } as DOMRect);
    spyOnProperty(window, 'scrollY', 'get').and.returnValue(620);

    expect(service.captureSnapshot('personal')).toEqual({
      position: [window.scrollX, 620],
      anchor: { key: 'personal', viewportOffset: 140 },
    });
  });

  it('restores relative to the anchor after content height changes', () => {
    const anchor = document.createElement('section');
    anchor.dataset['scrollAnchor'] = 'personal';
    anchor.dataset['pageScrollTest'] = '';
    document.body.appendChild(anchor);
    spyOn(anchor, 'getBoundingClientRect').and.returnValue({ top: 260 } as DOMRect);
    spyOnProperty(window, 'scrollY', 'get').and.returnValue(500);
    const snapshot: PageScrollSnapshot = {
      position: [0, 500],
      anchor: { key: 'personal', viewportOffset: 100 },
    };

    service.restoreSnapshot(snapshot);

    expect(windowScroll).toHaveBeenCalledOnceWith({ left: 0, top: 660, behavior: 'auto' });
  });

  it('falls back to the absolute position if the anchor disappeared', () => {
    service.restoreSnapshot({
      position: [12, 480],
      anchor: { key: 'missing', viewportOffset: 80 },
    });

    expect(windowScroll).toHaveBeenCalledOnceWith({ left: 12, top: 480, behavior: 'auto' });
  });

  it('turns smooth scrolling off when reduced motion is requested', () => {
    spyOn(window, 'matchMedia').and.returnValue({ matches: true } as MediaQueryList);
    service.scrollToPosition([0, 300], 'smooth');

    expect(windowScroll).toHaveBeenCalledOnceWith({ left: 0, top: 300, behavior: 'auto' });
  });

  it('cancels a stale restoration when a newer operation begins', async () => {
    const frames: FrameRequestCallback[] = [];
    spyOn(window, 'requestAnimationFrame').and.callFake((callback) => {
      frames.push(callback);
      return frames.length;
    });
    const first = service.restoreSnapshotAfterNextRender({ position: [0, 100] });
    const second = service.restoreSnapshotAfterNextRender({ position: [0, 300] });

    frames[0](0);
    frames[1](0);

    await expectAsync(first).toBeResolvedTo(false);
    await expectAsync(second).toBeResolvedTo(true);
    expect(windowScroll).toHaveBeenCalledOnceWith({ left: 0, top: 300, behavior: 'auto' });
  });
});
