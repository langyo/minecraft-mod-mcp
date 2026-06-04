# Руководство по использованию CLI NPM MCP

**[English](../en/CLI.md)** &bull; **[简体中文](../zhs/CLI.md)** &bull; **[繁體中文](../zht/CLI.md)** &bull; **[日本語](../ja/CLI.md)** &bull; **[한국어](../ko/CLI.md)** &bull; **[Français](../fr/CLI.md)** &bull; **[Español](../es/CLI.md)** &bull; **Русский**

> Пакет `minecraft-mod-mcp` предоставляет полнофункциональный CLI для запуска клиентов и серверов Minecraft, управления версиями и аккаунтами, а также сборки SDK модов — всё из командной строки.

---

## Установка

```bash
npm install -g minecraft-mod-mcp
```

Или запустите напрямую без установки:

```bash
npx minecraft-mod-mcp
```

---

## Команды

### MCP сервер

Запустить MCP stdio сервер для интеграции с AI инструментами:

```bash
minecraft-mod-mcp
minecraft-mod-mcp mcp [options]
```

| Опция | Описание |
|--------|-------------|
| `--no-discover` | Не сканировать запущенный мод Minecraft |
| `--discover-timeout <ms>` | Таймаут обнаружения мода (по умолчанию: 300000) |

---

### Запуск клиента — `launch`

Запускает клиент Minecraft с указанной версией и загрузчиком модов.

```bash
minecraft-mod-mcp launch <version> [options]
```

| Опция | По умолчанию | Описание |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | Загрузчик модов |
| `--mc-dir <path>` | Авто | Директория игры |
| `--java <path>` | Автоопределение | Путь к исполняемому файлу Java |
| `--memory <mb>` | `2048` | Максимальный пул памяти JVM (МБ) |
| `--min-memory <mb>` | `512` | Минимальный пул памяти JVM (МБ) |
| `--jvm-args <args>` | — | Дополнительные аргументы JVM (через пробел) |
| `--game-args <args>` | — | Дополнительные аргументы игры (через пробел) |
| `--fullscreen` | `false` | Запустить в полноэкранном режиме |
| `--width <px>` | `854` | Ширина окна |
| `--height <px>` | `480` | Высота окна |
| `--server <host>` | — | Автоподключение к серверу при запуске |
| `--server-port <port>` | `25565` | Порт сервера |
| `--port <port>` | Авто | Порт мода MCP |
| `--mod-jar <path>` | — | JAR мода для внедрения |
| `--dry-run` | `false` | Показать команду без выполнения |

**Примеры:**

```bash
# Запуск с 4 ГБ ОЗУ, полноэкранный режим
minecraft-mod-mcp launch 1.21.11 --memory 4096 --fullscreen --loader fabric

# Запуск с пользовательскими флагами JVM, автоподключение к серверу
minecraft-mod-mcp launch 26.1.2 --jvm-args "-XX:+UseG1GC -Dfml.readTimeout=120" --server myserver.com

# Запуск в оконном режиме 1280x720
minecraft-mod-mcp launch 1.20.6 --width 1280 --height 720 --loader neoforge

# Предпросмотр команды запуска
minecraft-mod-mcp launch 1.21.11 --dry-run
```

---

### Выделенный сервер — `server`

Запускает выделенный сервер Minecraft.

```bash
minecraft-mod-mcp server <version> [options]
```

| Опция | По умолчанию | Описание |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | Загрузчик модов |
| `--java <path>` | Автоопределение | Путь к исполняемому файлу Java |
| `--memory <mb>` | `1024` | Максимальный пул памяти JVM (МБ) |
| `--min-memory <mb>` | — | Минимальный пул памяти JVM (МБ) |
| `--jvm-args <args>` | — | Дополнительные аргументы JVM (через пробел) |
| `--game-args <args>` | — | Дополнительные аргументы сервера (через пробел) |
| `--mod-jar <path>` | — | JAR мода для копирования в mods/ сервера |
| `--dry-run` | `false` | Показать команду без выполнения |

**Примеры:**

```bash
# Запуск сервера с 4 ГБ ОЗУ
minecraft-mod-mcp server 1.21.11 --memory 4096

# Запуск с пользовательской настройкой GC
minecraft-mod-mcp server 26.1.2 --jvm-args "-XX:+UseZGC -XX:+ZGenerational" --memory 8192

# Сервер Fabric с модом
minecraft-mod-mcp server 1.21.11 --loader fabric --mod-jar ./path/to/mod.jar
```

---

### Всё в одном (сервер + клиент) — `serve`

Одна команда: установка сервера + запуск сервера + запуск клиента (автоподключение).

```bash
minecraft-mod-mcp serve <version> [options]
```

| Опция | По умолчанию | Описание |
|--------|---------|-------------|
| `--loader <forge\|fabric\|neoforge>` | `forge` | Загрузчик модов |
| `--java <path>` | Автоопределение | Путь к исполняемому файлу Java |
| `--memory <mb>` | `2048` | Макс. память клиента (МБ) |
| `--min-memory <mb>` | — | Мин. память клиента (МБ) |
| `--server-memory <mb>` | `1024` | Макс. память сервера (МБ) |
| `--server-min-memory <mb>` | — | Мин. память сервера (МБ) |
| `--jvm-args <args>` | — | Доп. аргументы JVM для обоих |
| `--game-args <args>` | — | Доп. аргументы игры для клиента |
| `--server-game-args <args>` | — | Доп. аргументы для сервера |
| `--fullscreen` | `false` | Запустить клиент в полноэкранном режиме |
| `--width <px>` | `854` | Ширина окна клиента |
| `--height <px>` | `480` | Высота окна клиента |
| `--port <port>` | Авто | Порт MCP |
| `--mod-jar <path>` | — | JAR мода для обеих сторон |
| `--dry-run` | `false` | Показать план без выполнения |

**Пример:**

```bash
# Полное окружение: 4 ГБ клиент, 2 ГБ сервер, полный экран
minecraft-mod-mcp serve 1.21.11 --memory 4096 --server-memory 2048 --fullscreen
```

---

### Управление версиями

| Команда | Описание |
|---------|-------------|
| `minecraft-mod-mcp list` | Список всех поддерживаемых версий Minecraft |
| `minecraft-mod-mcp installed` | Список локально установленных версий |
| `minecraft-mod-mcp install <version> [--loader <l>]` | Загрузить и установить версию |

---

### Управление аккаунтами

| Команда | Описание |
|---------|-------------|
| `minecraft-mod-mcp auth login` | Войти через аккаунт Microsoft |
| `minecraft-mod-mcp auth offline <name>` | Создать офлайн аккаунт |
| `minecraft-mod-mcp auth list` | Список настроенных аккаунтов |
| `minecraft-mod-mcp auth select <uuid>` | Выбрать активный аккаунт |
| `minecraft-mod-mcp auth remove <uuid>` | Удалить аккаунт |

---

### Утилиты

| Команда | Описание |
|---------|-------------|
| `minecraft-mod-mcp java` | Обнаружить установленные версии Java |
| `minecraft-mod-mcp status` | Показать состояние подключения мода MCP |
| `minecraft-mod-mcp sdk <version> [--loader <l>] [--no-build]` | Собрать SDK мода для версии |

---

## Аргументы JVM / Игры

Опции `--jvm-args` и `--game-args` принимают аргументы, разделённые пробелами. В оболочках, разделяющих по пробелам, заключайте значение в кавычки:

```bash
minecraft-mod-mcp launch 1.21.11 --jvm-args "-XX:+UseG1GC -XX:MaxGCPauseMillis=50"
minecraft-mod-mcp server 1.21.11 --game-args "--port 25566 --max-players 10"
```

---

## JSON файл конфигурации

Расширенные значения по умолчанию можно задать в `~/.minecraft/mcp_launcher/config.json`:

```json
{
  "max_memory_mb": 4096,
  "min_memory_mb": 1024,
  "width": 1920,
  "height": 1080,
  "fullscreen": false,
  "java_args": "-XX:+UseG1GC",
  "game_args": "",
  "game_dir": "~/.minecraft/mcp_launcher/game",
  "mcp_port": 9876,
  "download_source": "bmclapi",
  "language": "ru-RU"
}
```

Флаги CLI всегда имеют приоритет над значениями из файла конфигурации.
