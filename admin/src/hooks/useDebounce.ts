import { useEffect, useState } from 'react';

/**
 * Debounce a value by a given delay (ms, default 400).
 * Returns the debounced value, which only updates after the delay has passed without changes.
 */
export function useDebounce<T>(value: T, delay: number = 400): T {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    return () => {
      clearTimeout(handler);
    };
  }, [value, delay]);

  return debouncedValue;
}
