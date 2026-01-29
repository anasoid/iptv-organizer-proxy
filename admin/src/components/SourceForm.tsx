import { useEffect, useState } from "react";
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
import type { Source } from "../services/sourcesApi";
import sourcesApi from "../services/sourcesApi";
import proxiesApi from "../services/proxiesApi";

interface SourceFormProps {
  source?: Source | null;
  onSuccess: () => void;
  onCancel: () => void;
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
  } = useForm<any>({
    defaultValues: source || {
      name: "",
      url: "",
      username: "",
      password: "",
      syncInterval: 1,
      isActive: true,
      proxyId: null,
      enableProxy: null,
      disableStreamProxy: null,
      streamFollowLocation: null,
      useRedirect: null,
      useRedirectXmltv: null,
    },
  });

  // eslint-disable-next-line react-hooks/incompatible-library
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

  const onSubmit = (data: any) => {
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
                  proxiesData.data.map((proxy: any) => (
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
            Proxy Configuration (Optional - leave unchecked to inherit from environment)
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
                        checked={Boolean(field.value)}
                        onChange={(e) => field.onChange(e.target.checked ? true : false)}
                        indeterminate={field.value === null}
                      />
                    }
                    label="Enable HTTP Proxy for Upstream Requests"
                  />
                )}
              />
              <Typography
                variant="caption"
                sx={{ display: "block", color: "text.secondary", ml: 4 }}
              >
                When enabled, all upstream requests from this source go through the
                configured HTTP proxy server
              </Typography>
            </Box>

            <Box>
              <Controller
                name="disableStreamProxy"
                control={control}
                render={({ field }) => (
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={Boolean(field.value)}
                        onChange={(e) => field.onChange(e.target.checked ? true : false)}
                        indeterminate={field.value === null}
                      />
                    }
                    label="Disable Stream Proxy Endpoint (Direct Redirects)"
                  />
                )}
              />
              <Typography
                variant="caption"
                sx={{ display: "block", color: "text.secondary", ml: 4 }}
              >
                When enabled, stream redirects are sent directly to client instead
                of routing through /proxy endpoint
              </Typography>
            </Box>
          </Box>

          <Box sx={{ mt: 2 }}>
            <Controller
              name="streamFollowLocation"
              control={control}
              render={({ field }) => (
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={Boolean(field.value)}
                      onChange={(e) => field.onChange(e.target.checked ? true : false)}
                      indeterminate={field.value === null}
                    />
                  }
                  label="Follow HTTP Redirects When Streaming"
                />
              )}
            />
            <Typography
              variant="caption"
              sx={{ display: "block", color: "text.secondary", ml: 4 }}
            >
              When enabled, the proxy will automatically follow HTTP redirects
              (301/302) from the upstream source when streaming content
            </Typography>
          </Box>
        </Box>

        <Box sx={{ borderTop: 1, borderColor: "divider", pt: 2, mt: 2, gridColumn: "1 / -1" }}>
          <Typography variant="subtitle2" sx={{ mb: 2, fontWeight: 600 }}>
            Stream & XMLTV Redirect Mode (Optional)
          </Typography>

          <Box sx={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 2 }}>
            <Box>
              <Controller
                name="useRedirect"
                control={control}
                render={({ field }) => (
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={Boolean(field.value)}
                        onChange={(e) => field.onChange(e.target.checked ? true : false)}
                        indeterminate={field.value === null}
                      />
                    }
                    label="Enable Stream Direct Redirect (302)"
                  />
                )}
              />
              <Typography
                variant="caption"
                sx={{ display: "block", color: "text.secondary", ml: 4 }}
              >
                When enabled, stream requests return a direct 302 redirect instead of proxying through /proxy endpoint
              </Typography>
            </Box>

            <Box>
              <Controller
                name="useRedirectXmltv"
                control={control}
                render={({ field }) => (
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={Boolean(field.value)}
                        onChange={(e) => field.onChange(e.target.checked ? true : false)}
                        indeterminate={field.value === null}
                      />
                    }
                    label="Enable XMLTV Direct Redirect (302)"
                  />
                )}
              />
              <Typography
                variant="caption"
                sx={{ display: "block", color: "text.secondary", ml: 4 }}
              >
                When enabled, XMLTV EPG requests return a direct 302 redirect instead of streaming content
              </Typography>
            </Box>
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
