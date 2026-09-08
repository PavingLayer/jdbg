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

    button.addEventListener('click', () => {
        const sidebar = document.getElementById('mdbook-sidebar');
        if (sidebar && !checkbox.checked) {
            sidebar.style.display = '';
            // Force reflow so the expansion animation runs (mdBook Safari workaround).
            sidebar.offsetHeight;
        }
        checkbox.checked = !checkbox.checked;
        checkbox.dispatchEvent(new Event('change'));
        syncExpanded();
    });

    checkbox.addEventListener('change', syncExpanded);
})();
