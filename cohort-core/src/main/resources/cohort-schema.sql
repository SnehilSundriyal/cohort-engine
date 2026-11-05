CREATE TABLE IF NOT EXISTS cohorts (
    id         text        PRIMARY KEY,
    name       text        NOT NULL,
    priority   integer     NOT NULL,
    rule_text  text        NOT NULL,
    version    integer     NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS cohort_memberships (
    user_id        text        NOT NULL,
    cohort_id      text        NOT NULL,
    cohort_version integer     NOT NULL,
    assigned_at    timestamptz NOT NULL,
    PRIMARY KEY (user_id, cohort_id)
);

CREATE INDEX IF NOT EXISTS cohort_memberships_cohort_idx ON cohort_memberships (cohort_id);
