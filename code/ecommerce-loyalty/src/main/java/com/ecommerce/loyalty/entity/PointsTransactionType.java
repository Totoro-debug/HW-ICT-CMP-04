package com.ecommerce.loyalty.entity;

/**
 * Types of points transactions in the loyalty system.
 */
public enum PointsTransactionType {

    /** Points earned from order payment, reviews, or promotional activities. */
    EARN,

    /** Points consumed to offset order payment. */
    REDEEM,

    /** Points moved from available balance to frozen balance. */
    FREEZE,

    /** Points moved from frozen balance back to available balance. */
    UNFREEZE,

    /** Frozen points finally consumed by the business operation. */
    CONSUME_FROZEN,

    /** Points removed due to expiry. */
    EXPIRE,

    /** Manual adjustment by an administrator. */
    ADJUST
}
