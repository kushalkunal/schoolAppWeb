-- =====================================================================================
-- Slice 33-34 — parent notification categories + daily-ops gap-filler feature flags.
--
-- Strategy: every new feature is registered in the features() catalog with default_enabled=
-- TRUE for "always-on" categories (absence, fee receipts) and FALSE for opt-in extras
-- (homework, birthday wishes). Tenant admins flip per-flag via existing FeatureOverride.
--
-- Plan mapping: parent notifications are bundled with STARTER+ (it's table stakes); the
-- daily-ops gap-fillers go to GROWTH/ENTERPRISE so smaller schools get a clean baseline.
-- =====================================================================================

INSERT INTO features (feature_key, name, description, default_enabled, category) VALUES
    -- Parent notification umbrella + per-category
    ('PARENT_NOTIFICATIONS',       'Parent notifications',       'Master switch for WA + email alerts to parents', TRUE,  'Communication'),
    ('PARENT_NOTIFY_ABSENCE',      'Notify on absence',          'Send WA/email when a student is marked absent',  TRUE,  'Communication'),
    ('PARENT_NOTIFY_LATE_ARRIVAL', 'Notify on late arrival',     'Send WA/email when a student is marked late',    TRUE,  'Communication'),
    ('PARENT_NOTIFY_FEE_RECEIPT',  'Notify on fee receipt',      'Send receipt PDF after payment',                 TRUE,  'Communication'),
    ('PARENT_NOTIFY_FEE_DUE',      'Notify on fee due soon',     'Daily cron — invoices due in 3 / 1 / 0 days',    TRUE,  'Communication'),
    ('PARENT_NOTIFY_FEE_OVERDUE',  'Notify on fee overdue',      'Daily cron — invoices past due date',            TRUE,  'Communication'),
    ('PARENT_NOTIFY_REPORT_CARD',  'Notify on report card',      'Send report card PDF when published',            TRUE,  'Communication'),
    ('PARENT_NOTIFY_MARKS',        'Notify on marks finalized',  'Per-subject marks push',                          FALSE, 'Communication'),
    ('PARENT_NOTIFY_HOMEWORK',     'Notify on homework assigned','Daily summary of homework for the child',         FALSE, 'Communication'),
    ('PARENT_NOTIFY_LIBRARY_OVERDUE','Notify library overdue',    'Daily — books past return date',                  FALSE, 'Communication'),
    ('PARENT_NOTIFY_EXAM_SCHEDULE','Notify exam schedule',       'Hall ticket + datesheet on publish',              TRUE,  'Communication'),
    ('PARENT_NOTIFY_CIRCULAR',     'Notify on circular',         'Auto-send every published circular',              TRUE,  'Communication'),
    ('PARENT_NOTIFY_BIRTHDAY',     'Birthday greeting',          'Send birthday wishes to student via parent',      FALSE, 'Communication'),

    -- Daily-ops gap fillers
    ('VISITOR_MANAGEMENT',     'Visitor log',           'Front-desk in/out + host staff + purpose',         FALSE, 'Operations'),
    ('CASH_RECONCILIATION',    'Day-end cash recon',    'Today''s collection by mode, drawer open/close',   TRUE,  'Finance'),
    ('EXPENSE_TRACKING',       'Expense tracking',      'Per-tenant expense entry with categories',         FALSE, 'Finance'),
    ('INCIDENT_LOG',           'Incident log',          'Behaviour / discipline / merit-demerit',           FALSE, 'Operations'),
    ('INFIRMARY_LOG',          'Infirmary visits',      'Nurse log + medication + parent notify',           FALSE, 'Operations'),
    ('STUDENT_DOCUMENT_VAULT', 'Student doc vault',     'Aadhaar, birth cert, prev marksheet attachments',  FALSE, 'Operations'),
    ('UNIFIED_INBOX',          'Unified inbox',         'Sent WA + SMS + email timeline per student',       FALSE, 'Communication'),
    ('ALERTS_FEED',            'Alerts feed',           'Persistent alerts inbox page',                     TRUE,  'Operations'),
    ('LOW_STOCK_ALERTS',       'Low-stock alerts',      'Inventory threshold notifications',                FALSE, 'Operations'),
    ('PTM_SCHEDULING',         'Parent-teacher meetings','Slot-based PTM booking',                          FALSE, 'Communication')
ON CONFLICT (feature_key) DO NOTHING;

-- Map parent-notifications to every plan above FREE so demos work out of the box.
WITH p AS (SELECT id, code FROM plans)
INSERT INTO plan_features (plan_id, feature_key)
SELECT p.id, f.feature_key
FROM p
CROSS JOIN LATERAL (VALUES
    ('STARTER',    'PARENT_NOTIFICATIONS'),
    ('STARTER',    'PARENT_NOTIFY_ABSENCE'),
    ('STARTER',    'PARENT_NOTIFY_LATE_ARRIVAL'),
    ('STARTER',    'PARENT_NOTIFY_FEE_RECEIPT'),
    ('STARTER',    'PARENT_NOTIFY_FEE_DUE'),
    ('STARTER',    'PARENT_NOTIFY_FEE_OVERDUE'),
    ('STARTER',    'PARENT_NOTIFY_REPORT_CARD'),
    ('STARTER',    'PARENT_NOTIFY_EXAM_SCHEDULE'),
    ('STARTER',    'PARENT_NOTIFY_CIRCULAR'),
    ('GROWTH',     'PARENT_NOTIFICATIONS'),
    ('GROWTH',     'PARENT_NOTIFY_ABSENCE'),
    ('GROWTH',     'PARENT_NOTIFY_LATE_ARRIVAL'),
    ('GROWTH',     'PARENT_NOTIFY_FEE_RECEIPT'),
    ('GROWTH',     'PARENT_NOTIFY_FEE_DUE'),
    ('GROWTH',     'PARENT_NOTIFY_FEE_OVERDUE'),
    ('GROWTH',     'PARENT_NOTIFY_REPORT_CARD'),
    ('GROWTH',     'PARENT_NOTIFY_MARKS'),
    ('GROWTH',     'PARENT_NOTIFY_HOMEWORK'),
    ('GROWTH',     'PARENT_NOTIFY_LIBRARY_OVERDUE'),
    ('GROWTH',     'PARENT_NOTIFY_EXAM_SCHEDULE'),
    ('GROWTH',     'PARENT_NOTIFY_CIRCULAR'),
    ('GROWTH',     'PARENT_NOTIFY_BIRTHDAY'),
    ('GROWTH',     'CASH_RECONCILIATION'),
    ('GROWTH',     'ALERTS_FEED'),
    ('GROWTH',     'STUDENT_DOCUMENT_VAULT'),
    ('GROWTH',     'UNIFIED_INBOX'),
    ('ENTERPRISE', 'PARENT_NOTIFICATIONS'),
    ('ENTERPRISE', 'PARENT_NOTIFY_ABSENCE'),
    ('ENTERPRISE', 'PARENT_NOTIFY_LATE_ARRIVAL'),
    ('ENTERPRISE', 'PARENT_NOTIFY_FEE_RECEIPT'),
    ('ENTERPRISE', 'PARENT_NOTIFY_FEE_DUE'),
    ('ENTERPRISE', 'PARENT_NOTIFY_FEE_OVERDUE'),
    ('ENTERPRISE', 'PARENT_NOTIFY_REPORT_CARD'),
    ('ENTERPRISE', 'PARENT_NOTIFY_MARKS'),
    ('ENTERPRISE', 'PARENT_NOTIFY_HOMEWORK'),
    ('ENTERPRISE', 'PARENT_NOTIFY_LIBRARY_OVERDUE'),
    ('ENTERPRISE', 'PARENT_NOTIFY_EXAM_SCHEDULE'),
    ('ENTERPRISE', 'PARENT_NOTIFY_CIRCULAR'),
    ('ENTERPRISE', 'PARENT_NOTIFY_BIRTHDAY'),
    ('ENTERPRISE', 'VISITOR_MANAGEMENT'),
    ('ENTERPRISE', 'CASH_RECONCILIATION'),
    ('ENTERPRISE', 'EXPENSE_TRACKING'),
    ('ENTERPRISE', 'INCIDENT_LOG'),
    ('ENTERPRISE', 'INFIRMARY_LOG'),
    ('ENTERPRISE', 'STUDENT_DOCUMENT_VAULT'),
    ('ENTERPRISE', 'UNIFIED_INBOX'),
    ('ENTERPRISE', 'ALERTS_FEED'),
    ('ENTERPRISE', 'LOW_STOCK_ALERTS'),
    ('ENTERPRISE', 'PTM_SCHEDULING')
) AS f(plan_code, feature_key)
WHERE p.code = f.plan_code
ON CONFLICT DO NOTHING;
