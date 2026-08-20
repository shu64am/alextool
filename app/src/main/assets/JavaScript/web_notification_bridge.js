(function() {
    if (window.__alextoolNotifInstalled) return;
    window.__alextoolNotifInstalled = true;
    var _seq = 0;
    var _pending = {};
    function AlexToolNotification(title, options) {
        if (!(this instanceof AlexToolNotification)) return;
        options = options || {};
        AlexToolNotificationBridge.postNotification(
            String(title || ''),
            String(options.body || ''),
            String(options.tag || ''),
            String(window.location.hostname || '')
        );
    }
    AlexToolNotification.prototype.close = function() {};
    Object.defineProperty(AlexToolNotification, 'permission', {
        get: function() {
            return AlexToolNotificationBridge.getPermissionState(String(window.location.hostname || ''));
        },
        configurable: true
    });
    AlexToolNotification.requestPermission = function(callback) {
        var id = String(++_seq);
        return new Promise(function(resolve) {
            _pending[id] = function(result) {
                delete _pending[id];
                if (typeof callback === 'function') callback(result);
                resolve(result);
            };
            AlexToolNotificationBridge.requestPermission(id, String(window.location.hostname || ''));
        });
    };
    window._AlexToolResolvePermission = function(id, result) {
        var cb = _pending[String(id)];
        if (cb) cb(result);
    };
    window.Notification = AlexToolNotification;
})();
