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
    // Margen para runners de CI lentos: Chrome se desconectaba por ping timeout (2000 ms por defecto) a mitad de la
    // batería sin ningún test fallido. Con tolerancia, Karma espera a que se reconecte en vez de abortar.
    pingTimeout: 30000,
    browserDisconnectTimeout: 30000,
    browserDisconnectTolerance: 2,
    browserNoActivityTimeout: 120000,
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
