package pbl6.ctsv.drl.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/** Identity of a writable DRL table. Never exposed through a public module boundary. */
@MappedSuperclass
public abstract class DrlEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`ID`", nullable = false)
    private Long id;

    public Long getId() {
        return id;
    }
}

