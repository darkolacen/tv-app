# TV App

Android TV app that displays a web page controlled by a remote API. The app polls an endpoint, receives a URL and configuration, and shows the page in a WebView.

## API contract

The app talks to your backend over HTTP. Implement the following so the app can receive the URL and settings.

### Endpoint

```
GET {base_url}/{deviceId}
```

- **base_url** – Configurable in the app (default can be set in code). Example: `http://your-server.com/api/tv`.
- **deviceId** – UUID generated once per device and sent on every request. Use it to identify the device and return device-specific config.

Example: `GET http://your-server.com/api/tv/a1b2c3d4-e5f6-7890-abcd-ef1234567890`

### Response format

Respond with **JSON** and `Content-Type: application/json`:

| Field         | Type   | Required | Description |
|---------------|--------|----------|-------------|
| `url`         | string | No       | Web page URL to show (must be `http` or `https`). If missing or invalid, the app keeps the current page or shows the device ID screen. |
| `refreshRate` | number | No       | How often the app should poll again, in **milliseconds**. Allowed range: 1000–300000 (1 s–5 min). Default if omitted: 5000. |
| `jsZoom`      | string | No       | JavaScript to run after each page load (e.g. for TV zoom). Passed to `WebView.loadUrl()`, so it must be a full string like `"javascript:document.body.style.zoom=1/window.devicePixelRatio;"`. If omitted, the app uses a default zoom script. |

### Example response

```json
{
  "url": "https://example.com/dashboard",
  "refreshRate": 10000,
  "jsZoom": "javascript:document.body.style.zoom=1/window.devicePixelRatio;"
}
```

- **Only update the page when URL changes** – The app compares the new `url` with the last one; it reloads the WebView only when the value actually changes.
- **refreshRate** – The next poll happens after this many milliseconds. Use it to throttle or speed up polling per device.
- **jsZoom** – Optional. Omit to use the app’s default; set to a custom script (e.g. empty string or different zoom) to override.

### Example: no URL (show device ID screen)

```json
{
  "refreshRate": 5000
}
```

Or return `{}` – the app will keep current state and poll again after the default or last `refreshRate`.

### Example: change page and polling interval

```json
{
  "url": "https://example.com/other-page",
  "refreshRate": 30000,
  "jsZoom": "javascript:document.body.style.zoom=1/window.devicePixelRatio;"
}
```

### Error handling

- If the request fails (network error, non-2xx, or invalid JSON), the app does not update the config and will retry after the last known `refreshRate` (or 5 seconds if none).
- The response body must be valid JSON starting with `{`. Otherwise the app treats it as a failed response.

## App behaviour (summary)

1. On launch, the app shows a **device ID** and optional **endpoint** / **polling status** until it has a valid `url` from the API.
2. It polls `GET {base_url}/{deviceId}`. Base URL can be changed in the app (EDIT ENDPOINT).
3. From the JSON response it updates stored `url`, `refreshRate`, and `jsZoom`. If `url` is new and valid, it loads it in the WebView.
4. The next poll is scheduled after `refreshRate` ms.
5. After each page load, the app runs the configured `jsZoom` script (or the default) so the page scales correctly on TV.

## Building

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Releases

See [Releases](https://github.com/darkolacen/tv-app/releases) for pre-built APKs.
