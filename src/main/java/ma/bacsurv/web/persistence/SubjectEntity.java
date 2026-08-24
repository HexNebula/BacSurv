package ma.bacsurv.web.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A subject this centre examines. */
@Entity
@Table(name = "center_subject")
public class SubjectEntity extends CatalogueEntry {

    protected SubjectEntity() {}

    public SubjectEntity(CenterEntity center, String name) {
        super(center, name);
    }
}
