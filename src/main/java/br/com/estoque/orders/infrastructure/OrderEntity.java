package br.com.estoque.orders.infrastructure;

import br.com.estoque.orders.domain.*;
import br.com.estoque.organizations.infrastructure.OrganizationEntity;
import java.time.OffsetDateTime;
import java.util.*;
import javax.persistence.*;

@Entity
@Table(name="pedidos")
public class OrderEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="id_externo", length=128, nullable=false, unique=true, updatable=false)
    private String externalId;
    @Column(name="codigo_orgao", length=64, nullable=false, updatable=false)
    private String organizationCode;
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="codigo_orgao", insertable=false, updatable=false)
    private OrganizationEntity organization;
    @Column(name="solicitante", length=255, nullable=false, updatable=false)
    private String requester;
    @Enumerated(EnumType.STRING)
    @Column(name="origem", length=20, nullable=false, updatable=false)
    private OrderSource source;
    @Column(name="assinatura", length=64, nullable=false, updatable=false)
    private String signature;
    @Column(name="data_hora", nullable=false, updatable=false, columnDefinition="timestamp with time zone")
    private OffsetDateTime createdAt;
    @Enumerated(EnumType.STRING)
    @Column(name="situacao", length=20, nullable=false)
    private OrderStatus status;
    @Column(name="data_cancelamento", columnDefinition="timestamp with time zone")
    private OffsetDateTime cancelledAt;
    @Column(name="motivo_cancelamento", length=500)
    private String cancellationReason;
    @OneToMany(mappedBy="order", cascade=CascadeType.PERSIST)
    private List<OrderItemEntity> items = new ArrayList<>();

    protected OrderEntity() {}
    static OrderEntity header(NewOrder request, OffsetDateTime createdAt) {
        var entity = new OrderEntity();
        entity.externalId = request.externalId();
        entity.organizationCode = request.organizationCode();
        entity.requester = request.requester();
        entity.source = request.source();
        entity.signature = request.signature();
        entity.createdAt = createdAt;
        entity.status = OrderStatus.CONFIRMADO;
        return entity;
    }
    long id() { return id; }
    void addItems(List<OrderLine> lines) {
        for (var line : lines) items.add(new OrderItemEntity(this, line));
    }
    void cancel(Order cancelled) {
        status = cancelled.status();
        cancelledAt = cancelled.cancelledAt();
        cancellationReason = cancelled.cancellationReason();
    }
    Order toDomain() {
        return new Order(id, new NewOrder(externalId, organizationCode, requester, source,
                items.stream().map(OrderItemEntity::toDomain).toList()), createdAt, status, cancelledAt, cancellationReason);
    }
}
