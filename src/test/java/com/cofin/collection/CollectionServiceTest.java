package com.cofin.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cofin.cafe.Cafe;
import com.cofin.cafe.CafeRepository;
import com.cofin.common.InvalidInputException;
import com.cofin.common.NotFoundException;
import com.cofin.user.AppUser;
import com.cofin.user.AppUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    static final long OWNER = 1L;
    static final long STRANGER = 2L;
    static final long LIST_ID = 10L;
    static final long CAFE_ID = 100L;

    @Mock
    CafeCollectionRepository collections;

    @Mock
    CollectionItemRepository items;

    @Mock
    CafeRepository cafes;

    @Mock
    AppUserRepository users;

    CollectionService service;

    CafeCollection list;

    @BeforeEach
    void setUp() {
        service = new CollectionService(collections, items, cafes, users);
        list = new CafeCollection(new AppUser("alice", "{noop}x"), "讀書用");
    }

    @Test
    void strangerCannotSeeSomeoneElsesList() {
        when(collections.findByIdAndOwnerId(LIST_ID, STRANGER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(STRANGER, LIST_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void strangerCannotDeleteSomeoneElsesList() {
        when(collections.findByIdAndOwnerId(LIST_ID, STRANGER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(STRANGER, LIST_ID)).isInstanceOf(NotFoundException.class);
        verify(collections, never()).delete(any());
    }

    @Test
    void createRejectsDuplicateName() {
        when(collections.existsByOwnerIdAndName(OWNER, "讀書用")).thenReturn(true);

        assertThatThrownBy(() -> service.create(OWNER, "  讀書用 "))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("讀書用");
        verify(collections, never()).save(any());
    }

    @Test
    void renameToSameNameIsAllowed() {
        when(collections.findByIdAndOwnerId(LIST_ID, OWNER)).thenReturn(Optional.of(list));

        service.rename(OWNER, LIST_ID, "讀書用");

        verify(collections, never()).existsByOwnerIdAndName(any(), any());
        assertThat(list.getName()).isEqualTo("讀書用");
    }

    @Test
    void addingCafeTwiceOnlyUpdatesNote() {
        Cafe cafe = new Cafe("貓下去", null, null);
        CollectionItem existing = new CollectionItem(list, cafe, "舊備註");
        when(collections.findByIdAndOwnerId(LIST_ID, OWNER)).thenReturn(Optional.of(list));
        when(cafes.findById(CAFE_ID)).thenReturn(Optional.of(cafe));
        when(items.findByCollectionIdAndCafeId(LIST_ID, CAFE_ID)).thenReturn(Optional.of(existing));

        boolean added = service.addItem(OWNER, LIST_ID, CAFE_ID, "新備註");

        assertThat(added).isFalse();
        assertThat(existing.getNote()).isEqualTo("新備註");
        verify(items, never()).save(any());
    }

    @Test
    void addingCafeTwiceWithoutNoteKeepsOldNote() {
        Cafe cafe = new Cafe("貓下去", null, null);
        CollectionItem existing = new CollectionItem(list, cafe, "舊備註");
        when(collections.findByIdAndOwnerId(LIST_ID, OWNER)).thenReturn(Optional.of(list));
        when(cafes.findById(CAFE_ID)).thenReturn(Optional.of(cafe));
        when(items.findByCollectionIdAndCafeId(LIST_ID, CAFE_ID)).thenReturn(Optional.of(existing));

        service.addItem(OWNER, LIST_ID, CAFE_ID, "  ");

        assertThat(existing.getNote()).isEqualTo("舊備註");
    }
}
