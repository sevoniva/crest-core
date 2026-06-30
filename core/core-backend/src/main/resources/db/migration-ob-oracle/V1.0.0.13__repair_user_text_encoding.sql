-- Repair built-in user text imported with an incorrect client character set in early OB Oracle builds.
UPDATE core_iam_user
SET name = UNISTR('\7BA1\7406\5458')
WHERE id = 1
  AND account = 'admin'
  AND name = UNISTR('\00E7\00AE\00A1\00E7\0090\2020\00E5\2018\02DC');
