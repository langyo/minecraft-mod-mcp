import { createI18n } from 'vue-i18n'

import deDE from '../../../shared/i18n/de-DE.json'
import enUS from '../../../shared/i18n/en-US.json'
import esES from '../../../shared/i18n/es-ES.json'
import frFR from '../../../shared/i18n/fr-FR.json'
import jaJP from '../../../shared/i18n/ja-JP.json'
import koKR from '../../../shared/i18n/ko-KR.json'
import zhCN from '../../../shared/i18n/zh-CN.json'
import zhTW from '../../../shared/i18n/zh-TW.json'

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
