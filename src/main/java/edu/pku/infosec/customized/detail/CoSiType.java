package edu.pku.infosec.customized.detail;

public enum CoSiType {
    INTRA_SHARD_PREPARE,
    INTRA_SHARD_COMMIT,
    INPUT_LOCK_PREPARE,
    INPUT_LOCK_COMMIT,
    INPUT_INVALID_PROOF,
    INPUT_UNLOCK_PREPARE,
    INPUT_UNLOCK_COMMIT,
    OUTPUT_PREPARE,
    OUTPUT_COMMIT
}