(function() {
    if (window.__alextoolLinkTrackerInstalled) return;
    window.__alextoolLinkTrackerInstalled = true;
    window.__alextoolLastTouchedLinkText = '';
    document.addEventListener('touchstart', function(e) {
        var el = e.target;
        while (el) {
            if (el.tagName === 'A') {
                window.__alextoolLastTouchedLinkText = (el.textContent || '').trim().replace(/\s+/g, ' ').substring(0, 200);
                return;
            }
            el = el.parentElement;
        }
    }, true);
})();
