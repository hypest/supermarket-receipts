module.exports = {
  presets: ['module:@react-native/babel-preset'],
  plugins: [ // Add the plugins array for react-native-dotenv
    [
      'module:react-native-dotenv',
      {
        moduleName: '@env',
        path: '.env',
        blacklist: null,
        whitelist: null,
        safe: false,
        allowUndefined: true,
      },
    ],
  ],
};
