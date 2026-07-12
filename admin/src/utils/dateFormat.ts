export function formatDisplayDate(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }

  const isoMatch = value.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (isoMatch) {
    const [, year, month, day] = isoMatch;
    return `${day}/${month}/${year}`;
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleDateString('en-GB');
}

export function formatDisplayDateTime(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleString('en-GB', {
    hour12: false,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

export function toIsoDateFromDisplay(value: string): string | undefined {
  const trimmed = value.trim();
  if (!trimmed) {
    return undefined;
  }

  if (/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) {
    return trimmed;
  }

  const match = trimmed.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
  if (!match) {
    return undefined;
  }

  const [, day, month, year] = match;
  const isoValue = `${year}-${month}-${day}`;
  const parsed = new Date(`${isoValue}T00:00:00Z`);
  if (Number.isNaN(parsed.getTime())) {
    return undefined;
  }

  const normalized = parsed.toISOString().slice(0, 10);
  return normalized === isoValue ? isoValue : undefined;
}

