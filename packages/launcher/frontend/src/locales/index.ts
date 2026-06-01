import { createI18n } from 'vue-i18n'

import deDE from '@i18n/de-DE.json'
import enUS from '@i18n/en-US.json'
import esES from '@i18n/es-ES.json'
import frFR from '@i18n/fr-FR.json'
import jaJP from '@i18n/ja-JP.json'
import koKR from '@i18n/ko-KR.json'
import zhCN from '@i18n/zh-CN.json'
import zhTW from '@i18n/zh-TW.json'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  fallbackLocale: 'en-US',
  messages: {
    'zh-CN': zhCN,
    'zh-TW': zhTW,
    'en-US': enUS,
    'ja-JP': jaJP,
    'ko-KR': koKR,
    'de-DE': deDE,
    'fr-FR': frFR,
    'es-ES': esES,
  },
})

export default i18n
