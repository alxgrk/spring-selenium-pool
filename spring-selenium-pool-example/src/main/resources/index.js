chrome.runtime.onMessageExternal.addListener(
    function (request, sender, sendResponse) {
        if (request && request.extension === "init") {
            // do whatever you want to
            sendResponse("extension responded")
        }
        if (request && request.extension === "setProxy") {
            var proxy = request.proxyJson
            var config =
                {
                    mode: "fixed_servers",
                    rules: {singleProxy: {port: proxy.port, scheme: "http", host: proxy.ip}, bypassList: ["localhost"]}
                };
            chrome.proxy.settings.set({value: config, scope: 'regular'}, function () {
                sendResponse()
            });
        }
    }
);
