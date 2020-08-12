chrome.runtime.onMessageExternal.addListener(
    function (request, sender, sendResponse) {
        if (request && request.extension === "init") {
            // do whatever you want to
            sendResponse("extension responded")
        }
    }
);