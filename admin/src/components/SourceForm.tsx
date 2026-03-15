import { useEffect } from "react";
import {
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  CircularProgress,
  Alert,
  FormControlLabel,
  Checkbox,
  Box,
  Typography,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
} from "@mui/material";
import { useForm, Controller } from "react-hook-form";
import { useMutation, useQuery } from "@tanstack/react-query";
import type { Source } from "../types";
import sourcesApi from "../services/sourcesApi";
import proxiesApi from "../services/proxiesApi";

interface SourceFormProps {
  source?: Source | null;
  onSuccess: () => void;
  onCancel: () => void;
}

interface SourceFormData extends Omit<Source, "id" | "created_at" | "updated_at"> {
  proxyId?: number | null;
}

interface Proxy {
  id: number;
  name: string;
}

export default function SourceForm({
  source,
  onSuccess,
  onCancel,
}: SourceFormProps) {

  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
    watch,
    control,
  } = useForm<SourceFormData>({
    defaultValues: source || {
      name: "",
      url: "",
      username: "",
      password: "",
      syncInterval: 1,
      isActive: true,
      proxyId: null,
      enableProxy: false,
      connectXtreamApi: null,
      connectXtreamStream: null,
      connectXmltv: null,
    },
  });

  const urlValue = watch("url");

  // Create mutation
  const createMutation = useMutation({
    mutationFn: (data: Omit<Source, "id" | "created_at" | "updated_at">) =>
      sourcesApi.createSource(data),
    onSuccess,
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: (data: { id: number; source: Partial<Source> }) =>
      sourcesApi.updateSource(data.id, data.source),
    onSuccess,
  });

  // Test connection mutation
  const testMutation = useMutation({
    mutationFn: (id: number) => sourcesApi.testConnection(id),
  });

  // Fetch proxies for dropdown
  const { data: proxiesData } = useQuery({
    queryKey: ['proxies'],
    queryFn: () => proxiesApi.getProxies(1, 100),
  });

  useEffect(() => {
    if (source) {
      reset(source);
    }
  }, [source, reset]);

  const onSubmit = (data: SourceFormData) => {
    if (source?.id) {
      updateMutation.mutate({
        id: source.id,
        source: data,
      });
    } else {
      createMutation.mutate(data);
    }
  };

  const handleTestConnection = async () => {
    if (source?.id) {
      testMutation.mutate(source.id);
    }
  };

  const isLoading = createMutation.isPending || updateMutation.isPending;
  const error = createMutation.error || updateMutation.error;

  return (
    <>
      <DialogTitle>{source ? "Edit Source" : "Add Source"}</DialogTitle>
      <DialogContent
        sx={{ pt: 2, display: "grid", gridTemplateColumns: "1fr 1fr", gap: 2 }}
      >
        {error && (
          <Alert severity="error" sx={{ gridColumn: "1 / -1" }}>
            {error instanceof Error ? error.message : "An error occurred"}
          </Alert>
        )}
        {testMutation.data && (
          <Alert severity="success" sx={{ gridColumn: "1 / -1" }}>
            {testMutation.data.connected
              ? "Connection successful!"
              : "Connection failed"}
          </Alert>
        )}

        <TextField
          label="Name"
          fullWidth
          {...register("name", { required: "Name is required" })}
          error={!!errors.name}
          helperText={errors.name?.message}
        />

        <TextField
          label="Sync Interval (days)"
          fullWidth
          type="number"
          inputProps={{ min: 1 }}
          {...register("syncInterval", {
            required: "Sync interval is required",
            min: { value: 1, message: "Minimum is 1 day" },
            valueAsNumber: true,
          })}
          error={!!errors.syncInterval}
          helperText={errors.syncInterval?.message}
        />

        <TextField
          label="URL"
          fullWidth
          type="url"
          {...register("url", {
            required: "URL is required",
            pattern: {
              value: /^https?:\/\/.+/,
              message: "Please enter a valid URL",
            },
          })}
          error={!!errors.url}
          helperText={errors.url?.message}
          sx={{ gridColumn: "1 / -1" }}
        />

        <TextField
          label="Username"
          fullWidth
          {...register("username", { required: "Username is required" })}
          error={!!errors.username}
          helperText={errors.username?.message}
        />

        <TextField
          label="Password"
          fullWidth
          type="text"
          {...register("password", { required: "Password is required" })}
          error={!!errors.password}
          helperText={errors.password?.message}
        />

        <Box sx={{ gridColumn: "1 / -1" }}>
          <FormControlLabel
            control={
              <Checkbox
                {...register('isActive')}
                defaultChecked={!source || source.isActive === true}
              />
            }
            label="Active"
          />
        </Box>

        <Controller
          name="proxyId"
          control={control}
          defaultValue={null}
          render={({ field }) => (
            <FormControl fullWidth>
              <InputLabel>Proxy Configuration</InputLabel>
              <Select
                value={field.value ?? ''}
                onChange={(e) => {
                  const val = e.target.value;
                  field.onChange(val === '' ? null : Number(val));
                }}
                label="Proxy Configuration"
              >
                <MenuItem value="">None (No Proxy)</MenuItem>
                {Array.isArray(proxiesData?.data) &&
                  proxiesData.data.map((proxy: Proxy) => (
                    <MenuItem key={proxy.id} value={proxy.id}>
                      {proxy.name}
                    </MenuItem>
                  ))}
              </Select>
            </FormControl>
          )}
        />

        <Box sx={{ borderTop: 1, borderColor: "divider", pt: 2, mt: 2, gridColumn: "1 / -1" }}>
          <Typography variant="subtitle2" sx={{ mb: 2, fontWeight: 600 }}>
            Proxy Settings (Optional)
          </Typography>

          <Box sx={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 2 }}>
            <Box>
              <Controller
                name="enableProxy"
                control={control}
                render={({ field }) => (
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={field.value === true}
                        onChange={(e) => field.onChange(e.target.checked)}
                      />
                    }
                    label="Enable HTTP Proxy"
                  />
                )}
              />
              <Typography
                variant="caption"
                sx={{ display: "block", color: "text.secondary", ml: 4 }}
              >
                When enabled, all upstream requests go through the configured HTTP proxy
              </Typography>
            </Box>
          </Box>
        </Box>


        <Box sx={{ borderTop: 1, borderColor: "divider", pt: 2, mt: 2, gridColumn: "1 / -1" }}>
          <Typography variant="subtitle2" sx={{ mb: 2, fontWeight: 600 }}>
            Connection Mode Settings (Optional)
          </Typography>

          <Box sx={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 2 }}>
            <Controller
              name="connectXtreamApi"
              control={control}
              render={({ field }) => (
                <FormControl fullWidth>
                  <InputLabel>Xtream API Mode</InputLabel>
                  <Select
                    value={field.value ?? ''}
                    onChange={(e) => {
                      const val = e.target.value;
                      field.onChange(val === '' ? null : val);
                    }}
                    label="Xtream API Mode"
                  >
                    <MenuItem value="DEFAULT">Default</MenuItem>
                    <MenuItem value="NO_PROXY">No Proxy</MenuItem>
                  </Select>
                </FormControl>
              )}
            />

            <Controller
              name="connectXtreamStream"
              control={control}
              render={({ field }) => (
                <FormControl fullWidth>
                  <InputLabel>Xtream Stream Mode</InputLabel>
                  <Select
                    value={field.value ?? ''}
                    onChange={(e) => {
                      const val = e.target.value;
                      field.onChange(val === '' ? null : val);
                    }}
                    label="Xtream Stream Mode"
                  >
                    <MenuItem value="DIRECT">Direct</MenuItem>
                    <MenuItem value="NO_PROXY">No Proxy</MenuItem>
                    <MenuItem value="PROXY">Proxy</MenuItem>
                    <MenuItem value="REDIRECT">Redirect</MenuItem>
                    <MenuItem value="DEFAULT">Default</MenuItem>
                  </Select>
                </FormControl>
              )}
            />

            <Controller
              name="connectXmltv"
              control={control}
              render={({ field }) => (
                <FormControl fullWidth>
                  <InputLabel>XMLTV Mode</InputLabel>
                  <Select
                    value={field.value ?? ''}
                    onChange={(e) => {
                      const val = e.target.value;
                      field.onChange(val === '' ? null : val);
                    }}
                    label="XMLTV Mode"
                  >
                    <MenuItem value="REDIRECT">Redirect</MenuItem>
                    <MenuItem value="TUNNEL">Tunnel</MenuItem>
                    <MenuItem value="NO_PROXY">No Proxy</MenuItem>
                    <MenuItem value="DEFAULT">Default</MenuItem>
                  </Select>
                </FormControl>
              )}
            />
          </Box>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel}>Cancel</Button>
        {source?.id && urlValue && (
          <Button
            onClick={handleTestConnection}
            disabled={testMutation.isPending}
            variant="outlined"
          >
            {testMutation.isPending ? "Testing..." : "Test Connection"}
          </Button>
        )}
        <Button
          onClick={handleSubmit(onSubmit)}
          variant="contained"
          disabled={isLoading}
        >
          {isLoading && <CircularProgress size={20} sx={{ mr: 1 }} />}
          {source ? "Update" : "Create"}
        </Button>
      </DialogActions>
    </>
  );
}
