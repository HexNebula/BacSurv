package ma.bacsurv.web.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A filière this centre runs.
 *
 * <p>Distinct from {@link StreamEntity}, which is one filière <em>within a
 * session</em> together with the rooms it occupies there. This is the centre's
 * catalogue: the list a session picks from.
 */
@Entity
@Table(name = "center_stream")
public class CenterStreamEntity extends CatalogueEntry {

    protected CenterStreamEntity() {}

    public CenterStreamEntity(CenterEntity center, String name) {
        super(center, name);
    }
}
