package com.bugbusters.backend.model;

/** Estado de uma revisão da campanha. Propostas nunca participam do cálculo produtivo. */
public enum StatusVersaoCampanha {
    PROPOSTA,
    PUBLICADA,
    CANCELADA
}
