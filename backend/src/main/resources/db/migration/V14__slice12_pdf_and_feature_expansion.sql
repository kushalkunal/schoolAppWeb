-- =====================================================================================
-- Slice 12 — PDF generation pipeline + broad feature expansion
-- =====================================================================================
-- Adds:
--   1. document_templates: per-school HTML override for any document type. Without a row,
--      the renderer falls back to the classpath template shipped with the JAR.
--   2. The full set of new feature_keys introduced by Slices 12-21. Defaults are off; the
--      ENTERPRISE plan gets them all, others get a curated subset.
-- =====================================================================================

CREATE TABLE document_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    -- Identifier matches DocumentType enum values: RECEIPT, TC, BONAFIDE, HALL_TICKET, REPORT_CARD, etc.
    document_type   VARCHAR(40) NOT NULL,
    -- Thymeleaf HTML fragment. Renderer resolves model variables (school, student, fee, exam).
    html_template   TEXT NOT NULL,
    -- Optional CSS override; concatenated with default stylesheet.
    css_override    TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_id   UUID,
    UNIQUE(school_id, document_type)
);
CREATE INDEX idx_document_templates_school ON document_templates(school_id);

-- =====================================================================================
-- New feature keys covering Slices 12-21. Kept screaming-snake; never rename.
-- =====================================================================================
INSERT INTO features (feature_key, name, description, default_enabled, category) VALUES
    -- Slice 12: PDF generation
    ('PDF_GENERATION',      'PDF generation',                'Server-side PDF rendering for receipts, TCs, hall tickets, report cards', FALSE, 'Documents'),
    ('TRANSFER_CERTIFICATE','Transfer certificate',          'Generate TC PDF on student exit',                                          FALSE, 'Documents'),
    ('BONAFIDE_CERTIFICATE','Bonafide certificate',          'One-click bonafide PDF for current students',                              FALSE, 'Documents'),
    ('HALL_TICKETS',        'Hall tickets',                  'Bulk-generate exam admit cards per section',                              FALSE, 'Documents'),

    -- Slice 13: Bulk import
    ('BULK_IMPORT',         'Bulk CSV import',               'Excel/CSV upload for students, staff, fee structures',                    FALSE, 'Onboarding'),

    -- Slice 14: Notification expansion
    ('SMS_FALLBACK',        'SMS fallback',                  'Send via SMS when WhatsApp delivery fails or parent opted out',           FALSE, 'Communication'),
    ('PUSH_NOTIFICATIONS',  'Push notifications',            'Mobile push via FCM / APNs',                                              FALSE, 'Communication'),
    ('TRANSLATION',         'Auto-translation',              'Translate circulars + comments per parent language preference',          FALSE, 'Communication'),
    ('VOICE_CALLS',         'Voice / video calls',           'In-app audio / video sessions',                                            FALSE, 'Communication'),

    -- Slice 15: Fee depth
    ('LATE_FEE_AUTOMATION', 'Late fee automation',           'Auto-apply late fee N days after due date',                               FALSE, 'Finance'),
    ('FEE_DISCOUNTS',       'Discounts & concessions',       'Sibling discount, merit scholarship, financial-aid waivers',              FALSE, 'Finance'),
    ('FEE_REFUNDS',         'Refunds & adjustments',         'Refund + ledger adjustments with audit trail',                            FALSE, 'Finance'),
    ('FEE_INSTALLMENTS',    'Multi-installment plans',       'Split a fee head into N installments with separate due dates',            FALSE, 'Finance'),
    ('FEE_GST',             'GST on fee heads',              'Per-head GST flag + GSTIN-tagged receipt',                                FALSE, 'Finance'),

    -- Slice 16: HR
    ('STAFF_ATTENDANCE',    'Staff attendance',              'Mark / view staff attendance separately from students',                  FALSE, 'HR'),
    ('LEAVE_MANAGEMENT',    'Leave management',              'Leave applications + approval workflow',                                  FALSE, 'HR'),
    ('PAYROLL',             'Payroll',                       'Salary structure, payslip generation, PF/ESI calc',                       FALSE, 'HR'),

    -- Slice 17: Admissions
    ('ADMISSIONS_FUNNEL',   'Admissions funnel',             'Public enquiry → application → test → offer → enrolled',                  FALSE, 'Admissions'),

    -- Slice 18: Auxiliary modules
    ('HOSTEL',              'Hostel / boarding',             'Room allocation, warden, visitor logs, mess plans',                       FALSE, 'Boarding'),
    ('CAFETERIA',           'Cafeteria',                     'Meal plans, pre-paid card / wallet, daily menu',                          FALSE, 'Boarding'),
    ('INVENTORY',           'Inventory / assets',            'Asset register, issue/return, maintenance, depreciation',                 FALSE, 'Operations'),

    -- Slice 19: AI
    ('AI_RISK_SCORING',     'AI risk scoring',               'Attendance + marks + behavior → at-risk student alerts',                  FALSE, 'AI'),
    ('AI_CHATBOT',          'AI parent chatbot',             'LLM-backed FAQ for parents (PTM dates, fee balance, etc.)',               FALSE, 'AI'),
    ('AI_AUTO_GRADE',       'AI answer-sheet grading',       'OCR + LLM scoring of scanned handwritten answer sheets',                 FALSE, 'AI'),

    -- Slice 20: Govt integrations (India)
    ('UDISE_EXPORT',        'UDISE+ export',                 'Annual govt-mandated school data export',                                 FALSE, 'Compliance'),
    ('DIGILOCKER_PUSH',     'DigiLocker push',               'Push TCs / marksheets to student DigiLocker',                             FALSE, 'Compliance'),
    ('DIKSHA_SYNC',         'DIKSHA content sync',           'NCERT DIKSHA learning resources for teachers',                            FALSE, 'Compliance'),
    ('NAD_INTEGRATION',     'NAD integration',               'National Academic Depository certificate registration',                   FALSE, 'Compliance'),

    -- Slice 21: Power features
    ('AUTO_TIMETABLE',      'Auto-timetable generator',      'Constraint-solver based weekly timetable',                                FALSE, 'Operations'),
    ('PERIOD_ATTENDANCE',   'Period-wise attendance',        'Per-period attendance for subject teachers',                              FALSE, 'Operations'),

    -- Extra ops
    ('BIOMETRIC_DEVICES',   'Biometric / RFID devices',      'Sync with attendance hardware (ESSL, Realtime)',                          FALSE, 'Integrations'),
    ('FACE_RECOGNITION',    'Face recognition attendance',   'Camera-based attendance via face match',                                  FALSE, 'AI'),
    ('GPS_TRANSPORT',       'Live bus GPS',                  'Real-time bus location + ETA push to parents',                            FALSE, 'Transport'),

    -- Document vault (Slice 12 boundary)
    ('DOCUMENT_VAULT',      'Student document vault',        'Per-student secured file vault for Aadhaar, birth cert, TC, photo',       FALSE, 'Documents')
ON CONFLICT (feature_key) DO NOTHING;

-- Bundle the new features into plans. Logic:
--   - Documents (RECEIPT-class)            → STARTER+ (every paid plan gets PDFs)
--   - Bulk import                          → STARTER+
--   - Notification expansion (SMS, Push)   → GROWTH+
--   - Fee depth (LATE_FEE, DISCOUNTS, etc) → GROWTH+
--   - HR module                            → GROWTH+
--   - Admissions, Hostel, Inventory        → ENTERPRISE
--   - AI features                          → ENTERPRISE (or per-tenant override on lower plans)
--   - Govt integrations                    → ENTERPRISE
--   - Power features (timetable solver)    → ENTERPRISE
WITH p AS (SELECT id, code FROM plans)
INSERT INTO plan_features (plan_id, feature_key)
SELECT p.id, f.feature_key
FROM p
CROSS JOIN LATERAL (VALUES
    -- STARTER+ : core documents
    ('STARTER',    'PDF_GENERATION'),
    ('STARTER',    'TRANSFER_CERTIFICATE'),
    ('STARTER',    'BONAFIDE_CERTIFICATE'),
    ('STARTER',    'HALL_TICKETS'),
    ('STARTER',    'BULK_IMPORT'),
    ('STARTER',    'DOCUMENT_VAULT'),

    -- GROWTH+ : richer comms + fee depth + HR
    ('GROWTH',     'PDF_GENERATION'),
    ('GROWTH',     'TRANSFER_CERTIFICATE'),
    ('GROWTH',     'BONAFIDE_CERTIFICATE'),
    ('GROWTH',     'HALL_TICKETS'),
    ('GROWTH',     'BULK_IMPORT'),
    ('GROWTH',     'DOCUMENT_VAULT'),
    ('GROWTH',     'SMS_FALLBACK'),
    ('GROWTH',     'PUSH_NOTIFICATIONS'),
    ('GROWTH',     'TRANSLATION'),
    ('GROWTH',     'LATE_FEE_AUTOMATION'),
    ('GROWTH',     'FEE_DISCOUNTS'),
    ('GROWTH',     'FEE_REFUNDS'),
    ('GROWTH',     'FEE_INSTALLMENTS'),
    ('GROWTH',     'FEE_GST'),
    ('GROWTH',     'STAFF_ATTENDANCE'),
    ('GROWTH',     'LEAVE_MANAGEMENT'),
    ('GROWTH',     'PERIOD_ATTENDANCE'),

    -- ENTERPRISE : everything
    ('ENTERPRISE', 'PDF_GENERATION'),
    ('ENTERPRISE', 'TRANSFER_CERTIFICATE'),
    ('ENTERPRISE', 'BONAFIDE_CERTIFICATE'),
    ('ENTERPRISE', 'HALL_TICKETS'),
    ('ENTERPRISE', 'BULK_IMPORT'),
    ('ENTERPRISE', 'DOCUMENT_VAULT'),
    ('ENTERPRISE', 'SMS_FALLBACK'),
    ('ENTERPRISE', 'PUSH_NOTIFICATIONS'),
    ('ENTERPRISE', 'TRANSLATION'),
    ('ENTERPRISE', 'VOICE_CALLS'),
    ('ENTERPRISE', 'LATE_FEE_AUTOMATION'),
    ('ENTERPRISE', 'FEE_DISCOUNTS'),
    ('ENTERPRISE', 'FEE_REFUNDS'),
    ('ENTERPRISE', 'FEE_INSTALLMENTS'),
    ('ENTERPRISE', 'FEE_GST'),
    ('ENTERPRISE', 'STAFF_ATTENDANCE'),
    ('ENTERPRISE', 'LEAVE_MANAGEMENT'),
    ('ENTERPRISE', 'PAYROLL'),
    ('ENTERPRISE', 'ADMISSIONS_FUNNEL'),
    ('ENTERPRISE', 'HOSTEL'),
    ('ENTERPRISE', 'CAFETERIA'),
    ('ENTERPRISE', 'INVENTORY'),
    ('ENTERPRISE', 'AI_RISK_SCORING'),
    ('ENTERPRISE', 'AI_CHATBOT'),
    ('ENTERPRISE', 'AI_AUTO_GRADE'),
    ('ENTERPRISE', 'UDISE_EXPORT'),
    ('ENTERPRISE', 'DIGILOCKER_PUSH'),
    ('ENTERPRISE', 'DIKSHA_SYNC'),
    ('ENTERPRISE', 'NAD_INTEGRATION'),
    ('ENTERPRISE', 'AUTO_TIMETABLE'),
    ('ENTERPRISE', 'PERIOD_ATTENDANCE'),
    ('ENTERPRISE', 'BIOMETRIC_DEVICES'),
    ('ENTERPRISE', 'FACE_RECOGNITION'),
    ('ENTERPRISE', 'GPS_TRANSPORT')
) AS f(plan_code, feature_key)
WHERE p.code = f.plan_code
ON CONFLICT DO NOTHING;
