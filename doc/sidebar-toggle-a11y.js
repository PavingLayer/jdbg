// Convert mdBook's sidebar toggle from <label> to <button>.
// <label> does not support aria-expanded / aria-controls, which fails
// Lighthouse's "Elements must only use supported ARIA attributes".
(function () {
    const checkbox = document.getElementById('mdbook-sidebar-toggle-anchor');
    const toggle = document.getElementById('mdbook-sidebar-toggle');
    if (!checkbox || !toggle || toggle.tagName !== 'LABEL') {
        return;
    }

    const button = document.createElement('button');
    button.type = 'button';
    button.id = toggle.id;
    button.className = toggle.className;
    if (toggle.title) {
        button.title = toggle.title;
    }
    ['aria-label', 'aria-controls', 'aria-expanded'].forEach((name) => {
        const value = toggle.getAttribute(name);
        if (value !== null) {
            button.setAttribute(name, value);
        }
    });
    while (toggle.firstChild) {
        button.appendChild(toggle.firstChild);
    }
    toggle.replaceWith(button);

    function syncExpanded() {
        button.setAttribute('aria-expanded', checkbox.checked ? 'true' : 'false');
    }

    button.addEventListener('click', (event) => {
        event.stopImmediatePropagation();
        const sidebar = document.getElementById('mdbook-sidebar');
        const expanding = !checkbox.checked;
        if (expanding && sidebar) {
            sidebar.style.display = '';
            requestAnimationFrame(() => {
                checkbox.checked = true;
                checkbox.dispatchEvent(new Event('change'));
                syncExpanded();
            });
            return;
        }
        checkbox.checked = false;
        checkbox.dispatchEvent(new Event('change'));
        syncExpanded();
    }, true);

    checkbox.addEventListener('change', syncExpanded);
})();
