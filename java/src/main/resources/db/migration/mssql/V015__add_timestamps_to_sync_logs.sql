-- Add created_at and updated_at columns to sync_logs table for audit trail

ALTER TABLE sync_logs
ADD created_at DATETIME2 DEFAULT GETDATE(),
    updated_at DATETIME2 DEFAULT GETDATE();
