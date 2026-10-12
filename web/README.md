# vinta-web

VINTA (halal ad network) for websites, PWAs and Telegram mini apps.

```sh
npm install vinta-web
```

```ts
import { loadVinta } from "vinta-web";

const VINTA = await loadVinta({ appKey: "va_your_app_key", test: true });

await VINTA.banner(document.getElementById("ad")!, "<banner unit>");
await VINTA.showInterstitial("<unit>");
const r = await VINTA.showRewarded("<unit>", { userId: user.id }); // grant from your SSV callback
const ad = await VINTA.native("<native unit>");
```

Get your app key and ad unit ids from the VINTA publisher console.
The package loads VINTA's official script (`https://site.view2earn.org/vinta/sdk.js`),
so you always run the current, signed SDK. Calls never reject: failures resolve
as "not shown".

No script bundler? Add the tag yourself instead:

```html
<script src="https://site.view2earn.org/vinta/sdk.js"></script>
```
