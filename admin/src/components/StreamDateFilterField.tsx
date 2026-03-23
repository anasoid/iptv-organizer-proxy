import { useRef, useState } from 'react';
import { IconButton, InputAdornment, TextField, type TextFieldProps } from '@mui/material';
import { CalendarMonth as CalendarMonthIcon } from '@mui/icons-material';
import { formatDisplayDate, toIsoDateFromDisplay } from '../utils/dateFormat';

interface StreamDateFilterFieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  size?: TextFieldProps['size'];
}

export default function StreamDateFilterField({
  label,
  value,
  onChange,
  size = 'small',
}: StreamDateFilterFieldProps) {
  const hiddenInputRef = useRef<HTMLInputElement | null>(null);
  const [draftValue, setDraftValue] = useState<string | null>(null);
  const displayValue = draftValue ?? (value ? formatDisplayDate(value) : '');

  const openPicker = () => {
    const input = hiddenInputRef.current;
    if (!input) {
      return;
    }

    if (typeof input.showPicker === 'function') {
      input.showPicker();
      return;
    }

    input.click();
  };

  const handleTextChange = (newValue: string) => {
    setDraftValue(newValue);

    if (!newValue.trim()) {
      onChange('');
      return;
    }

    const isoValue = toIsoDateFromDisplay(newValue);
    if (isoValue) {
      onChange(isoValue);
      setDraftValue(null);
    }
  };

  const handleBlur = () => {
    if (!displayValue.trim()) {
      setDraftValue(null);
      onChange('');
      return;
    }

    const isoValue = toIsoDateFromDisplay(displayValue);
    if (isoValue) {
      onChange(isoValue);
      setDraftValue(null);
      return;
    }

    setDraftValue(null);
  };

  return (
    <>
      <TextField
        label={label}
        placeholder="dd/mm/YYYY"
        value={displayValue}
        onChange={(e) => handleTextChange(e.target.value)}
        onBlur={handleBlur}
        size={size}
        InputLabelProps={{ shrink: true }}
        inputProps={{ inputMode: 'numeric' }}
        InputProps={{
          endAdornment: (
            <InputAdornment position="end">
              <IconButton edge="end" onClick={openPicker} aria-label={`Open ${label} picker`}>
                <CalendarMonthIcon fontSize="small" />
              </IconButton>
            </InputAdornment>
          ),
        }}
      />
      <input
        ref={hiddenInputRef}
        type="date"
        value={value}
        onChange={(e) => {
          setDraftValue(null);
          onChange(e.target.value);
        }}
        tabIndex={-1}
        aria-hidden="true"
        style={{
          position: 'absolute',
          width: 0,
          height: 0,
          opacity: 0,
          pointerEvents: 'none',
        }}
      />
    </>
  );
}


