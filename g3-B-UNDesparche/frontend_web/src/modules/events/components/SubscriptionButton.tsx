import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { useAuth } from '../../../context/AuthContext';
import { useSubscriptions } from '../../../api/hooks/useSubscriptions';
import { EventStatus } from '../../../types/event';
import { GuestSubscriptionDialog } from './GuestSubscriptionDialog';

export interface SubscriptionButtonProps {
  eventId: number;
  eventName: string;
  eventStatus: EventStatus;
  published: boolean;
  organizerId?: number | null;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

export function SubscriptionButton({
  eventId,
  eventName,
  eventStatus,
  published,
  organizerId,
  size = 'md',
  className = '',
}: SubscriptionButtonProps): React.JSX.Element {
  const { profile, token, isAuthenticated } = useAuth();
  const [dialogOpen, setDialogOpen] = useState(false);

  const { isSubscribed, loading, toggleSubscription, subscribe } = useSubscriptions(
    eventId,
    token
  );

  const isOrganizer = Boolean(profile?.id && organizerId && profile.id === organizerId);
  const isFinishedOrCancelled = eventStatus === 'FIN' || eventStatus === 'CAN';
  const isDraft = !published;
  const isDisabled = isFinishedOrCancelled || isDraft || loading;

  const sizeClasses = {
    sm: 'px-3 py-1 text-xs gap-1',
    md: 'px-4 py-2 text-sm gap-1.5',
    lg: 'px-6 py-3 text-base gap-2 w-full justify-center',
  }[size];

  if (isOrganizer) {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full bg-[#00D592]/15 px-3 py-1 font-bold text-xs text-[#00875A] dark:text-[#00F5A0] border border-[#00D592]/30">
        <span className="material-symbols-outlined text-sm" aria-hidden="true">
          verified
        </span>
        Organizador
      </span>
    );
  }

  const handleClick = async () => {
    if (!isAuthenticated) {
      if (isSubscribed) {
        await toggleSubscription();
      } else {
        setDialogOpen(true);
      }
    } else {
      await toggleSubscription();
    }
  };

  const handleGuestSubmit = async (guestEmail: string) => {
    await subscribe(guestEmail);
  };

  return (
    <>
      <motion.button
        type="button"
        whileTap={{ scale: 0.95 }}
        disabled={isDisabled}
        onClick={handleClick}
        aria-label={
          isSubscribed
            ? `Cancelar suscripción a ${eventName}`
            : `Suscribirme a las notificaciones de ${eventName}`
        }
        className={`inline-flex items-center rounded-full font-bold shadow-xs transition-all focus-visible:outline-2 focus-visible:outline-[#00D592] disabled:cursor-not-allowed disabled:opacity-50 ${sizeClasses} ${
          isSubscribed
            ? 'border border-[#00D592]/40 bg-[#00D592]/15 text-[#00875A] dark:text-[#00F5A0] hover:bg-[#00D592]/25'
            : 'heroui-btn-solid'
        } ${className}`}
      >
        {loading ? (
          <span className="material-symbols-outlined animate-spin text-base" aria-hidden="true">
            progress_activity
          </span>
        ) : (
          <span className="material-symbols-outlined text-base" aria-hidden="true">
            {isSubscribed ? 'notifications_active' : 'notifications'}
          </span>
        )}
        <span>{isSubscribed ? 'Suscrito' : 'Suscribirme'}</span>
      </motion.button>

      <GuestSubscriptionDialog
        open={dialogOpen}
        eventName={eventName}
        onClose={() => setDialogOpen(false)}
        onSubmit={handleGuestSubmit}
      />
    </>
  );
}
