"use client";

import { ThemeProvider, useTheme } from "next-themes";
import { createContext, useCallback, useContext, useState, type ReactNode } from "react";
import { Toaster } from "sonner";
import { TooltipProvider } from "./ui/tooltip";

const AnnounceContext = createContext<(message: string) => void>(() => {});

/** Announce async status changes (run finished, approval decided) to screen readers. */
export function useAnnounce() {
  return useContext(AnnounceContext);
}

function ThemedToaster() {
  const { resolvedTheme } = useTheme();
  return (
    <Toaster
      theme={resolvedTheme === "light" ? "light" : "dark"}
      richColors
      closeButton
      position="bottom-right"
    />
  );
}

export function Providers({ children }: { children: ReactNode }) {
  const [message, setMessage] = useState("");
  const announce = useCallback((m: string) => {
    setMessage("");
    requestAnimationFrame(() => setMessage(m));
  }, []);
  return (
    <ThemeProvider attribute="class" defaultTheme="dark" enableSystem={false}>
      <TooltipProvider>
        <AnnounceContext.Provider value={announce}>
          {children}
          <div aria-live="polite" aria-atomic="true" className="sr-only" data-testid="live-region">
            {message}
          </div>
          <ThemedToaster />
        </AnnounceContext.Provider>
      </TooltipProvider>
    </ThemeProvider>
  );
}
