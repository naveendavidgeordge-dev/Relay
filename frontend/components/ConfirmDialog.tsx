"use client";

import { useState, type ReactNode } from "react";
import { Button, type ButtonProps } from "./ui/button";
import { Dialog, DialogClose, DialogContent, DialogTrigger } from "./ui/dialog";

/** A trigger button that asks for confirmation before running `onConfirm`. */
export function ConfirmDialog({
  trigger,
  title,
  description,
  confirmLabel,
  confirmVariant = "primary",
  onConfirm,
  withComment = false,
  children,
}: {
  trigger: ReactNode;
  title: string;
  description?: ReactNode;
  confirmLabel: string;
  confirmVariant?: ButtonProps["variant"];
  onConfirm: (comment: string) => Promise<void> | void;
  withComment?: boolean;
  children?: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [comment, setComment] = useState("");
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent title={title} description={description}>
        {children}
        {withComment ? (
          <label className="mb-4 block text-xs text-muted">
            Comment (optional)
            <textarea
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              rows={3}
              className="mt-1 w-full rounded-md border border-border bg-bg p-2 text-sm text-fg"
            />
          </label>
        ) : null}
        <div className="flex justify-end gap-2">
          <DialogClose asChild>
            <Button variant="ghost">Cancel</Button>
          </DialogClose>
          <Button
            variant={confirmVariant}
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              try {
                await onConfirm(comment);
                setOpen(false);
              } finally {
                setBusy(false);
              }
            }}
          >
            {busy ? "Working…" : confirmLabel}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
