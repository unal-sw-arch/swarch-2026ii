package com.parosurvivors.chat.shared.infrastructure.persistence;

import com.parosurvivors.chat.shared.application.ports.UnitOfWork;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transacción por unidad de trabajo. READ COMMITTED: tras bloquear la fila de la conversación (FOR UPDATE), toda
 * lectura posterior ve lo que ya confirmó el envío anterior, sin depender del momento en que InnoDB toma su
 * instantánea (REPEATABLE READ). Es lo que hace fiable el reconocimiento de reenvíos por client_message_id.
 */
@Component
class TransactionalUnitOfWork implements UnitOfWork {

    private final TransactionTemplate template;

    TransactionalUnitOfWork(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
        this.template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        return template.execute(status -> work.get());
    }
}
