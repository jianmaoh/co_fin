// Slide-out drawer on the cafe page: clicking a person shows their review without leaving the page.
// Each person's content is server-rendered into a <template>; this script only copies it into the drawer.
(function () {
    const drawer = document.getElementById('participant-drawer');
    if (!drawer) {
        return;
    }
    const body = drawer.querySelector('.drawer-body');
    let opener = null;

    document.querySelectorAll('[data-participant]').forEach((button) => {
        button.addEventListener('click', () => {
            const template = document.getElementById(button.dataset.participant);
            body.replaceChildren(template.content.cloneNode(true));
            opener = button;
            drawer.showModal();
        });
    });

    drawer.querySelector('.drawer-close').addEventListener('click', () => drawer.close());

    // Clicking the dimmed backdrop (outside the panel) closes it, like Esc does.
    drawer.addEventListener('click', (event) => {
        if (event.target === drawer) {
            drawer.close();
        }
    });

    // Return focus to the person that was clicked, for keyboard users.
    drawer.addEventListener('close', () => {
        if (opener) {
            opener.focus();
        }
    });
})();
