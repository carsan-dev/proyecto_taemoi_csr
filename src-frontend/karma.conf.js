module.exports = function (config) {
  config.set({
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma'),
    ],
    reporters: ['progress'],
    customLaunchers: {
      ChromeHeadlessStable: {
        base: 'ChromeHeadless',
        flags: [
          '--no-sandbox',
          '--disable-gpu',
          '--disable-gpu-compositing',
          '--disable-software-rasterizer',
          '--disable-dev-shm-usage',
          '--disable-features=Vulkan,WebGPU',
        ],
      },
    },
  });
};
