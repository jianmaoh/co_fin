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
};

// Google calls this when the key is rejected (wrong key, or this site is not in its allowed referrers).
window.gm_authFailure = function () {
    showStatus('place-status', 'Google Maps 金鑰無法使用，請手動輸入店名和地址。', true);
    showStatus('map-status', 'Google Maps 金鑰無法使用，地圖無法顯示。', true);
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
