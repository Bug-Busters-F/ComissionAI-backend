package com.bugbusters.backend.service;

import com.bugbusters.backend.repository.ExecucaoCalculoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;

/** Progress is committed independently; it never confirms calculation results. */
@Service
public class ExecucaoCalculoProgressoService {
    private final ExecucaoCalculoRepository repository;
    private final TransactionTemplate isolated;
    public ExecucaoCalculoProgressoService(ExecucaoCalculoRepository repository,PlatformTransactionManager manager) {
        this.repository=repository;
        isolated=new TransactionTemplate(manager); isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public void registrar(UUID id,UUID tentativa,int itens) {
        isolated.executeWithoutResult(tx -> repository.progresso(id,tentativa,itens));
    }
}
