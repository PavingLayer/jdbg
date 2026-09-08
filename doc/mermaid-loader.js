// Load mermaid.min.js only on pages that contain diagrams.
(function () {
    if (!document.querySelector('pre.mermaid, .mermaid')) {
        return;
    }

    const src = window.mermaidSrc;
    if (!src) {
        return;
    }

    const darkThemes = ['ayu', 'navy', 'coal'];
    const classList = document.documentElement.classList;
    let lastThemeWasLight = true;
    for (let i = 0; i < classList.length; i++) {
        if (darkThemes.indexOf(classList[i]) !== -1) {
            lastThemeWasLight = false;
            break;
        }
    }
    const theme = lastThemeWasLight ? 'default' : 'dark';

    const script = document.createElement('script');
    script.src = src;
    script.onload = function () {
        if (window.mermaid) {
            window.mermaid.initialize({ startOnLoad: true, theme: theme });
        }
    };
    document.head.appendChild(script);

    const list = document.getElementById('mdbook-theme-list');
    if (!list) {
        return;
    }
    list.addEventListener('click', function (event) {
        const button = event.target.closest('button.theme');
        if (!button || !button.id) {
            return;
        }
        const name = button.id.replace(/^mdbook-theme-/, '');
        const goingDark = darkThemes.indexOf(name) !== -1;
        if (goingDark === lastThemeWasLight) {
            window.location.reload();
        }
    });
})();
