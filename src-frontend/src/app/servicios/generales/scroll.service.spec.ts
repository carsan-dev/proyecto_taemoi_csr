import { ApplicationRef } from '@angular/core';
import { ScrollService } from './scroll.service';

describe('ScrollService', () => {
  let service: ScrollService;

  beforeEach(() => {
    const applicationRef = {
      whenStable: () => Promise.resolve(),
    } as Pick<ApplicationRef, 'whenStable'>;

    service = new ScrollService(applicationRef as ApplicationRef);
  });

  it('scrolls instantly without delegating to smooth CSS behavior', () => {
    const scrollingElement = document.scrollingElement as HTMLElement;
    scrollingElement.scrollTop = 500;
    scrollingElement.scrollLeft = 20;
    const windowScroll = spyOn(window, 'scrollTo');

    service.scrollToTopInstant();

    expect(scrollingElement.scrollTop).toBe(0);
    expect(scrollingElement.scrollLeft).toBe(0);
    expect(windowScroll).not.toHaveBeenCalled();
  });

  it('keeps explicit smooth scrolling for user-triggered actions', () => {
    const windowScroll = spyOn(window, 'scrollTo');

    service.scrollToTop('smooth');

    expect(windowScroll).toHaveBeenCalledOnceWith({ top: 0, left: 0, behavior: 'smooth' });
  });

  it('waits for Angular stability and a rendered frame', async () => {
    const scrollingElement = document.scrollingElement as HTMLElement;
    scrollingElement.scrollTop = 500;
    const animationFrame = spyOn(window, 'requestAnimationFrame').and.callFake((callback) => {
      callback(0);
      return 1;
    });

    await service.scrollToTopAfterRender();

    expect(animationFrame).toHaveBeenCalled();
    expect(scrollingElement.scrollTop).toBe(0);
  });
});
