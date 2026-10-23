package com.cofin.collection;

import com.cofin.cafe.Cafe;
import com.cofin.cafe.CafeRepository;
import com.cofin.common.InvalidInputException;
import com.cofin.common.Names;
import com.cofin.common.NotFoundException;
import com.cofin.user.AppUserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CollectionService {

    static final int NAME_MAX_LENGTH = 30;
    static final int NOTE_MAX_LENGTH = 200;

    private final CafeCollectionRepository collections;
    private final CollectionItemRepository items;
    private final CafeRepository cafes;
    private final AppUserRepository users;

    public CollectionService(CafeCollectionRepository collections, CollectionItemRepository items,
                             CafeRepository cafes, AppUserRepository users) {
        this.collections = collections;
        this.items = items;
        this.cafes = cafes;
        this.users = users;
    }

    public List<CollectionSummary> listFor(Long ownerId) {
        return collections.summarize(ownerId);
    }

    public CollectionDetail detail(Long ownerId, Long collectionId) {
        CafeCollection collection = owned(ownerId, collectionId);
        List<CollectionItemView> views = items.findWithCafe(collectionId).stream()
                .map(i -> new CollectionItemView(i.getCafe().getId(), i.getCafe().getName(),
                        i.getCafe().getAddress(), i.getNote(), i.getAddedAt()))
                .toList();
        return new CollectionDetail(collection.getId(), collection.getName(), views);
    }

    public List<CollectionRef> collectionsContaining(Long ownerId, Long cafeId) {
        return items.findCollectionsContaining(cafeId, ownerId);
    }

    @Transactional
    public Long create(Long ownerId, String rawName) {
        String name = Names.required(rawName, NAME_MAX_LENGTH, "清單名稱");
        if (collections.existsByOwnerIdAndName(ownerId, name)) {
            throw duplicateName(name);
        }
        return collections.save(new CafeCollection(users.getReferenceById(ownerId), name)).getId();
    }

    @Transactional
    public void rename(Long ownerId, Long collectionId, String rawName) {
        CafeCollection collection = owned(ownerId, collectionId);
        String name = Names.required(rawName, NAME_MAX_LENGTH, "清單名稱");
        if (!name.equals(collection.getName()) && collections.existsByOwnerIdAndName(ownerId, name)) {
            throw duplicateName(name);
        }
        collection.rename(name);
    }

    /** The database cascades the delete to the list's items. */
    @Transactional
    public void delete(Long ownerId, Long collectionId) {
        collections.delete(owned(ownerId, collectionId));
    }

    /**
     * Adds a cafe to a list. If it is already there, only the note is updated (when one is given).
     *
     * @return true if the cafe was newly added
     */
    @Transactional
    public boolean addItem(Long ownerId, Long collectionId, Long cafeId, String rawNote) {
        CafeCollection collection = owned(ownerId, collectionId);
        Cafe cafe = cafes.findById(cafeId).orElseThrow(() -> new NotFoundException("找不到這家咖啡廳"));
        String note = Names.optional(rawNote, NOTE_MAX_LENGTH, "備註");

        Optional<CollectionItem> existing = items.findByCollectionIdAndCafeId(collectionId, cafeId);
        if (existing.isPresent()) {
            if (note != null) {
                existing.get().changeNote(note);
            }
            return false;
        }
        items.save(new CollectionItem(collection, cafe, note));
        return true;
    }

    @Transactional
    public void removeItem(Long ownerId, Long collectionId, Long cafeId) {
        owned(ownerId, collectionId);
        items.findByCollectionIdAndCafeId(collectionId, cafeId).ifPresent(items::delete);
    }

    private CafeCollection owned(Long ownerId, Long collectionId) {
        return collections.findByIdAndOwnerId(collectionId, ownerId)
                .orElseThrow(() -> new NotFoundException("找不到這個清單"));
    }

    private static InvalidInputException duplicateName(String name) {
        return new InvalidInputException("你已經有叫「" + name + "」的清單了");
    }
}
