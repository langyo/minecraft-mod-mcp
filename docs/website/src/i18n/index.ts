import { createI18n } from 'vue-i18n'
import en from '../../res/i18n/en.json'
import zhCN from '../../res/i18n/zh-CN.json'
import zhTW from '../../res/i18n/zh-TW.json'
import ja from '../../res/i18n/ja.json'
import ko from '../../res/i18n/ko.json'
import es from '../../res/i18n/es.json'
import fr from '../../res/i18n/fr.json'
import ru from '../../res/i18n/ru.json'

const SUPPORTED = ['en', 'zh-CN', 'zh-TW', 'ja', 'ko', 'es', 'fr', 'ru']
const STORAGE_KEY = 'mmmcp-locale'

const savedLocale = typeof localStorage !== 'undefined' ? localStorage.getItem(STORAGE_KEY) : null
const browserLocale = typeof navigator !== 'undefined' ? navigator.language : 'en'

function detectLocale(): string {
  if (savedLocale && SUPPORTED.includes(savedLocale)) {
    return savedLocale
  }
  if (browserLocale.startsWith('zh')) {
    return browserLocale.includes('TW') || browserLocale.includes('HK') ? 'zh-TW' : 'zh-CN'
  }
  if (browserLocale.startsWith('ja')) return 'ja'
  if (browserLocale.startsWith('ko')) return 'ko'
  if (browserLocale.startsWith('es')) return 'es'
  if (browserLocale.startsWith('fr')) return 'fr'
  if (browserLocale.startsWith('ru')) return 'ru'
  return 'en'
}

const i18n = createI18n({
  legacy: false,
  locale: detectLocale(),
  fallbackLocale: 'en',
  messages: {
    en,
    'zh-CN': zhCN,
    'zh-TW': zhTW,
    ja,
    ko,
    es,
    fr,
    ru,
  },
})

export default i18n
