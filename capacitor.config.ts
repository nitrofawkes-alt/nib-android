import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.byshawn.nib',
  appName: 'Nib',
  webDir: 'www',
  server: {
    url: 'https://nib-companion.floot.app',
    cleartext: false,
    allowNavigation: ['nib-companion.floot.app']
  },
  android: {
    backgroundColor: '#0b0910'
  }
};

export default config;
