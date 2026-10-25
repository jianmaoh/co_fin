// Google Maps integration. Google's script tag calls coFinMapsReady once the API has loaded.
// Every page works without this script: it only enhances elements that are present.

const DEFAULT_CENTER = { lat: 25.0418, lng: 121.5437 }; // Taipei, used until we know where the user is
const NEARBY_RADIUS_METERS = 1500;
const NEARBY_MAX_RESULTS = 20; // the most Nearby Search returns in one call

window.coFinMapsReady = async function () {
    const search = document.getElementById('place-search');
    if (search) {
        await run(() => setUpPlaceSearch(search), 'place-status');
    }
    const cafeMap = document.getElementById('cafe-map');
    if (cafeMap) {
        await run(() => showCafeMap(cafeMap), 'map-status');
    }
    const exploreMap = document.getElementById('explore-map');
    if (exploreMap) {
        await run(() => setUpExplorer(exploreMap), 'nearby-status');
    }
};

// Google calls this when the key is rejected (wrong key, or this site is not in its allowed referrers).
window.gm_authFailure = function () {
    showStatus('place-status', 'Google Maps 金鑰無法使用，請手動輸入店名和地址。', true);
    showStatus('map-status', 'Google Maps 金鑰無法使用，地圖無法顯示。', true);
    showStatus('nearby-status', 'Google Maps 金鑰無法使用，暫時不能找附近的咖啡廳。', true);
};

async function run(task, statusId) {
    try {
        await task();
    } catch (e) {
        console.error(e);
        showStatus(statusId, 'Google Maps 載入失敗，請稍後再試。', true);
    }
}

function showStatus(id, text, isError) {
    const el = document.getElementById(id);
    if (!el) {
        return;
    }
    el.textContent = text;
    el.classList.toggle('field-error', Boolean(isError));
}

/**
 * A map with the site's dark look. With a custom Map ID (GOOGLE_MAPS_MAP_ID) its cloud style is used
 * as-is; otherwise Google's demo Map ID with the built-in dark color scheme.
 */
async function createMap(container, options) {
    const { Map } = await google.maps.importLibrary('maps');
    const customMapId = container.dataset.mapId;
    const config = {
        mapId: customMapId || 'DEMO_MAP_ID',
        mapTypeControl: false,
        streetViewControl: false,
        fullscreenControl: false,
        clickableIcons: false,
        ...options,
    };
    if (!customMapId) {
        const { ColorScheme } = await google.maps.importLibrary('core');
        if (ColorScheme) {
            config.colorScheme = ColorScheme.DARK;
        }
    }
    return new Map(container, config);
}

function pinElement(extraClass) {
    const pin = element('div', 'map-pin' + (extraClass ? ' ' + extraClass : ''));
    pin.append(element('span', 'map-pin-dot'));
    return pin;
}

// ---------- new-cafe page: Google place search ----------

async function setUpPlaceSearch(container) {
    const { PlaceAutocompleteElement } = await google.maps.importLibrary('places');
    const autocomplete = new PlaceAutocompleteElement({
        includedPrimaryTypes: ['cafe'],
        includedRegionCodes: ['tw'],
    });
    container.appendChild(autocomplete);

    const form = document.getElementById('cafe-form');
    const nameInput = form.querySelector('[name="name"]');
    const addressInput = form.querySelector('[name="address"]');
    const placeIdInput = form.querySelector('[name="placeId"]');
    const clearButton = document.getElementById('place-clear');

    autocomplete.addEventListener('gmp-select', async ({ placePrediction }) => {
        const place = placePrediction.toPlace();
        // Only request Essentials-tier fields; the display name already comes with the prediction.
        await place.fetchFields({ fields: ['id', 'formattedAddress'] });

        nameInput.value = placePrediction.mainText ? placePrediction.mainText.text : placePrediction.text.text;
        addressInput.value = place.formattedAddress || '';
        placeIdInput.value = place.id;
        showStatus('place-status', '已選擇「' + nameInput.value + '」，確認下面的資料後按「新增」。', false);
        clearButton.hidden = false;
    });

    clearButton.addEventListener('click', () => {
        placeIdInput.value = '';
        clearButton.hidden = true;
        showStatus('place-status', '已取消 Google 地點，這家店會以手動輸入的資料新增。', false);
    });
}

// ---------- cafe page: single map with a hover card ----------

async function showCafeMap(container) {
    const [{ AdvancedMarkerElement }, { Place }] = await Promise.all([
        google.maps.importLibrary('marker'),
        google.maps.importLibrary('places'),
    ]);

    // The location is fetched live by place id instead of being stored in our database.
    const place = new Place({ id: container.dataset.placeId });
    await place.fetchFields({ fields: ['location'] });

    const map = await createMap(container, { center: place.location, zoom: 16 });
    const pin = pinElement('in-cofin');
    const marker = new AdvancedMarkerElement({ map, position: place.location, content: pin, gmpClickable: true });

    const card = infoCard({
        name: container.dataset.name,
        address: container.dataset.address,
        rating: container.dataset.rating,
        reviewCount: Number(container.dataset.reviewCount || 0),
        tags: (container.dataset.tags || '').split(',').filter(Boolean),
    });
    const info = new google.maps.InfoWindow({ headerDisabled: true, content: card });
    const open = () => info.open({ map, anchor: marker, shouldFocus: false });
    pin.addEventListener('mouseenter', open);
    marker.addListener('click', open);
    map.addListener('click', () => info.close());
}

// ---------- home page: nearby explorer ----------

async function setUpExplorer(container) {
    const map = await createMap(container, { center: DEFAULT_CENTER, zoom: 14 });
    const info = new google.maps.InfoWindow({ headerDisabled: true });
    map.addListener('click', () => info.close());

    if (!navigator.geolocation) {
        showStatus('nearby-status', '這個瀏覽器不支援定位，下面列出 Co-Fin 的熱門咖啡廳。', true);
        return;
    }
    // Asked as soon as the page opens, so nearby cafes appear without an extra click.
    navigator.geolocation.getCurrentPosition(
        (position) => run(() => showNearby(map, info, position.coords), 'nearby-status'),
        (error) => showStatus('nearby-status', geolocationMessage(error), true),
        { enableHighAccuracy: false, timeout: 10000, maximumAge: 5 * 60 * 1000 },
    );
}

function geolocationMessage(error) {
    switch (error.code) {
        case error.PERMISSION_DENIED:
            return '你沒有允許使用位置，可以在瀏覽器網址列左側開啟位置權限。下面先列出 Co-Fin 的熱門咖啡廳。';
        case error.TIMEOUT:
            return '取得位置逾時，請重新整理再試一次。';
        default:
            return '目前無法取得你的位置。';
    }
}

async function showNearby(map, info, coords) {
    showStatus('nearby-status', '正在搜尋附近的咖啡廳…', false);
    const [{ Place, SearchNearbyRankPreference }, { AdvancedMarkerElement }] = await Promise.all([
        google.maps.importLibrary('places'),
        google.maps.importLibrary('marker'),
    ]);
    const here = { lat: coords.latitude, lng: coords.longitude };
    map.setCenter(here);
    new AdvancedMarkerElement({ map, position: here, content: element('div', 'me-dot'), title: '你的位置' });

    const { places } = await Place.searchNearby({
        fields: ['id', 'displayName', 'formattedAddress', 'location'],
        locationRestriction: { center: here, radius: NEARBY_RADIUS_METERS },
        includedPrimaryTypes: ['cafe'],
        maxResultCount: NEARBY_MAX_RESULTS,
        rankPreference: SearchNearbyRankPreference.DISTANCE,
        language: 'zh-TW',
        region: 'tw',
    });
    if (places.length === 0) {
        showStatus('nearby-status', '1.5 公里內沒有找到咖啡廳。', false);
        return;
    }

    const list = document.getElementById('nearby-list');
    const matches = await lookUpInCoFin(list.dataset.lookupUrl, places.map((p) => p.id));
    const bounds = new google.maps.LatLngBounds(here);
    list.replaceChildren();

    for (const place of places) {
        const match = matches.get(place.id);
        const entry = {
            place,
            match,
            distance: distanceMeters(here, place.location),
            pin: pinElement(match ? 'in-cofin' : null),
        };
        entry.marker = new AdvancedMarkerElement({
            map, position: place.location, content: entry.pin, title: place.displayName, gmpClickable: true,
        });
        entry.item = nearbyListItem(entry, list.dataset);
        list.append(entry.item);
        bounds.extend(place.location);

        const focus = () => {
            highlight(entry, true);
            info.setContent(infoCard(cardData(entry, list.dataset)));
            info.open({ map, anchor: entry.marker, shouldFocus: false });
        };
        // Hovering either the pin or the list row shows the same card, like Google Maps.
        entry.pin.addEventListener('mouseenter', focus);
        entry.pin.addEventListener('mouseleave', () => highlight(entry, false));
        entry.item.addEventListener('mouseenter', focus);
        entry.item.addEventListener('mouseleave', () => highlight(entry, false));
        entry.marker.addListener('click', () => {
            focus();
            entry.item.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        });
    }
    map.fitBounds(bounds, 48);
    showStatus('nearby-status', '找到 ' + places.length + ' 家咖啡廳，距離由近到遠。', false);
}

function highlight(entry, on) {
    entry.pin.classList.toggle('active', on);
    entry.item.classList.toggle('active', on);
    entry.marker.zIndex = on ? 1000 : null;
}

/** Asks our server which of these places are already in Co-Fin. Missing data just means "not in Co-Fin". */
async function lookUpInCoFin(url, placeIds) {
    const params = new URLSearchParams();
    placeIds.forEach((id) => params.append('ids', id));
    try {
        const response = await fetch(url + '?' + params, { headers: { Accept: 'application/json' } });
        if (!response.ok) {
            return new Map();
        }
        const matches = await response.json();
        return new Map(matches.map((m) => [m.placeId, m]));
    } catch (e) {
        console.error(e);
        return new Map();
    }
}

function cardData(entry, urls) {
    const { place, match } = entry;
    return {
        name: place.displayName,
        address: place.formattedAddress,
        distance: entry.distance,
        rating: match && match.averageRating != null ? match.averageRating.toFixed(1) : '',
        reviewCount: match ? match.reviewCount : 0,
        tags: match ? match.tags.map((t) => t.name) : [],
        href: match ? urls.cafeUrl + match.cafeId : null,
        onAdd: match ? null : () => addToCoFin(urls.createUrl, place),
    };
}

// Everything below builds DOM with textContent (never innerHTML), so place names cannot inject HTML.

function nearbyListItem(entry, urls) {
    const data = cardData(entry, urls);
    const item = document.createElement('li');

    const info = element('div', 'grow');
    if (data.href) {
        const link = element('a', 'item-title', data.name);
        link.href = data.href;
        info.append(link);
    } else {
        info.append(element('span', 'item-title', data.name));
    }
    info.append(ratingLine(data));
    if (data.address) {
        info.append(element('div', 'muted', data.address));
    }
    if (data.tags.length > 0) {
        info.append(tagChips(data.tags));
    }

    const side = element('div', 'stats');
    side.append(element('span', 'distance', formatDistance(entry.distance)));
    if (data.href) {
        side.append(element('span', 'badge', '已在 Co-Fin'));
    } else {
        const add = element('button', 'secondary small', '加入 Co-Fin');
        add.type = 'button';
        add.addEventListener('click', data.onAdd);
        side.append(add);
    }
    item.append(info, side);
    return item;
}

function infoCard(data) {
    const card = element('div', 'map-card');
    card.append(element('div', 'map-card-title', data.name));
    card.append(ratingLine(data));
    if (data.distance !== undefined) {
        card.append(element('div', 'map-card-meta', formatDistance(data.distance)));
    }
    if (data.address) {
        card.append(element('div', 'map-card-meta', data.address));
    }
    if (data.tags.length > 0) {
        card.append(tagChips(data.tags.slice(0, 4)));
    }
    if (data.href) {
        const link = element('a', 'map-card-action', '查看評論 →');
        link.href = data.href;
        card.append(link);
    } else if (data.onAdd) {
        const add = element('button', 'small', '加入 Co-Fin');
        add.type = 'button';
        add.addEventListener('click', data.onAdd);
        card.append(add);
    }
    return card;
}

function ratingLine(data) {
    const line = element('div', 'rating-line');
    if (data.reviewCount > 0) {
        line.append(element('span', 'star', '★'), element('span', null, data.rating),
            element('span', 'muted', '(' + data.reviewCount + ')'));
    } else {
        line.append(element('span', 'muted', data.href === null ? '還不在 Co-Fin' : '還沒有評論'));
    }
    return line;
}

function tagChips(tags) {
    const chips = element('div', 'chips');
    for (const tag of tags) {
        chips.append(element('span', 'chip', '#' + tag));
    }
    return chips;
}

/** Submits the same form as the "新增咖啡廳" page; the server redirects to the cafe's page. */
function addToCoFin(url, place) {
    const csrf = document.getElementById('csrf-token');
    const form = document.createElement('form');
    form.method = 'post';
    form.action = url;
    for (const [name, value] of [
        [csrf.name, csrf.value],
        ['name', place.displayName],
        ['address', place.formattedAddress || ''],
        ['placeId', place.id],
    ]) {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        input.value = value;
        form.append(input);
    }
    document.body.append(form);
    form.submit();
}

function element(tag, className, text) {
    const el = document.createElement(tag);
    if (className) {
        el.className = className;
    }
    if (text !== undefined && text !== null) {
        el.textContent = text;
    }
    return el;
}

/** Great-circle distance (haversine formula). */
function distanceMeters(a, b) {
    const R = 6371000;
    const toRad = (deg) => (deg * Math.PI) / 180;
    const lat1 = toRad(a.lat);
    const lat2 = toRad(b.lat());
    const dLat = lat2 - lat1;
    const dLng = toRad(b.lng() - a.lng);
    const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
    return 2 * R * Math.asin(Math.sqrt(h));
}

function formatDistance(meters) {
    const rounded = Math.round(meters / 10) * 10;
    return rounded < 1000 ? rounded + ' m' : (meters / 1000).toFixed(1) + ' km';
}
