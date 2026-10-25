// Google Maps integration. Google's script tag calls coFinMapsReady once the API has loaded.
// Every page works without this script: it only enhances elements that are present.

window.coFinMapsReady = async function () {
    const search = document.getElementById('place-search');
    if (search) {
        await run(() => setUpPlaceSearch(search), 'place-status');
    }
    const map = document.getElementById('cafe-map');
    if (map) {
        await run(() => showCafeMap(map), 'map-status');
    }
    const nearbyButton = document.getElementById('nearby-button');
    if (nearbyButton) {
        setUpNearby(nearbyButton);
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
        showStatus(statusId, 'Google Maps 載入失敗，請稍後再試，或手動輸入。', true);
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

async function showCafeMap(container) {
    const [{ Map }, { AdvancedMarkerElement }, { Place }] = await Promise.all([
        google.maps.importLibrary('maps'),
        google.maps.importLibrary('marker'),
        google.maps.importLibrary('places'),
    ]);

    // The location is fetched live by place id instead of being stored in our database.
    const place = new Place({ id: container.dataset.placeId });
    await place.fetchFields({ fields: ['location'] });

    const map = new Map(container, {
        center: place.location,
        zoom: 16,
        mapId: 'DEMO_MAP_ID',
        mapTypeControl: false,
        streetViewControl: false,
    });
    new AdvancedMarkerElement({ map, position: place.location, title: container.dataset.name });
}

// ---------- nearby recommendations (home page) ----------

const NEARBY_RADIUS_METERS = 1500;

function setUpNearby(button) {
    button.addEventListener('click', () => {
        if (!navigator.geolocation) {
            showStatus('nearby-status', '這個瀏覽器不支援定位。', true);
            return;
        }
        button.disabled = true;
        showStatus('nearby-status', '正在取得你的位置…', false);
        navigator.geolocation.getCurrentPosition(
            async (position) => {
                await run(() => findNearby(position.coords), 'nearby-status');
                button.disabled = false;
            },
            (error) => {
                showStatus('nearby-status', geolocationMessage(error), true);
                button.disabled = false;
            },
            { enableHighAccuracy: false, timeout: 10000, maximumAge: 5 * 60 * 1000 },
        );
    });
}

function geolocationMessage(error) {
    switch (error.code) {
        case error.PERMISSION_DENIED:
            return '你沒有允許使用位置。可以在瀏覽器網址列左側的設定裡開啟位置權限。';
        case error.TIMEOUT:
            return '取得位置逾時，請再試一次。';
        default:
            return '目前無法取得你的位置。';
    }
}

async function findNearby(coords) {
    showStatus('nearby-status', '正在搜尋附近的咖啡廳…', false);
    const { Place, SearchNearbyRankPreference } = await google.maps.importLibrary('places');
    const here = { lat: coords.latitude, lng: coords.longitude };

    const { places } = await Place.searchNearby({
        fields: ['id', 'displayName', 'formattedAddress', 'location'],
        locationRestriction: { center: here, radius: NEARBY_RADIUS_METERS },
        includedPrimaryTypes: ['cafe'],
        maxResultCount: 10,
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
    renderNearby(list, places, matches, here);
    document.getElementById('nearby-panel').hidden = false;
    showStatus('nearby-status', '找到 ' + places.length + ' 家咖啡廳。', false);
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

// Built with DOM APIs and textContent (never innerHTML), so place names cannot inject HTML.
function renderNearby(list, places, matches, here) {
    list.replaceChildren();
    for (const place of places) {
        const match = matches.get(place.id);
        const item = document.createElement('li');

        const info = element('div', 'grow');
        if (match) {
            const link = element('a', 'item-title', place.displayName);
            link.href = list.dataset.cafeUrl + match.cafeId;
            info.append(link);
        } else {
            info.append(element('span', 'item-title', place.displayName));
        }
        if (place.formattedAddress) {
            info.append(element('div', 'muted', place.formattedAddress));
        }
        if (match && match.tags.length > 0) {
            const chips = element('div', 'chips');
            for (const tag of match.tags) {
                const chip = element('span', 'chip');
                chip.append(element('span', null, '#' + tag.name), element('span', 'count', String(tag.count)));
                chips.append(chip);
            }
            info.append(chips);
        }

        const side = element('div', 'stats');
        side.append(element('span', 'distance', formatDistance(distanceMeters(here, place.location))));
        if (match) {
            side.append(element('span', 'badge', '已在 Co-Fin'));
        } else {
            const add = element('button', 'secondary small', '加入 Co-Fin');
            add.type = 'button';
            add.addEventListener('click', () => addToCoFin(list.dataset.createUrl, place));
            side.append(add);
        }

        item.append(info, side);
        list.append(item);
    }
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
    if (text !== undefined) {
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
