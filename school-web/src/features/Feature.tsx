'use client';

import { ReactNode } from 'react';
import { FEATURE_FLAGS, FeatureKey, isFeatureEnabled } from './featureFlags';

interface FeatureProps {
  flag: FeatureKey;
  /** Rendered when the flag is OFF. Defaults to nothing. */
  fallback?: ReactNode;
  children: ReactNode;
}

/**
 * Render-gate for global feature flags. Single-tenant frontend means the flag is build-time
 * constant; this component still renders conditionally so dead routes don't leak into the UI.
 *
 * <example>
 *   <Feature flag="UNIFIED_INBOX">
 *     <InboxPage />
 *   </Feature>
 * </example>
 */
export function Feature({ flag, fallback = null, children }: FeatureProps) {
  return <>{isFeatureEnabled(flag) ? children : fallback}</>;
}

/**
 * Hook variant for places that need the boolean inline (e.g. wrapping a Link with an `if`).
 * Returns the same value as {@link isFeatureEnabled}; the hook shape keeps it ergonomic in
 * components without coupling them to module-level imports.
 */
export function useFeature(flag: FeatureKey): boolean {
  return FEATURE_FLAGS[flag] === true;
}

/**
 * Multi-flag check — returns true only when ALL listed flags are enabled. Handy for nested
 * features ("show the matrix editor only when FEE + FEE_STRUCTURE are both on").
 */
export function useAllFeatures(...flags: FeatureKey[]): boolean {
  return flags.every((f) => FEATURE_FLAGS[f] === true);
}
