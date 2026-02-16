export function getCurrentRoute() {
    const hash = location.hash.replace(/^#\/?/, '');
    return hash || 'workbench';
}

export function navigateTo(route) {
    location.hash = '#/' + route;
}
