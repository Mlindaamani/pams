import { type PropsWithChildren } from 'react';
import { ForbiddenPage } from '@/features/system/ForbiddenPage';
import { useAbility } from './AbilityProvider';
import type { Action, Subject } from './ability';

interface Props {
  action: Action;
  subject: Subject;
}

export function RequireAbility({ action, subject, children }: PropsWithChildren<Props>) {
  const ability = useAbility();
  if (!ability.can(action, subject)) {
    return <ForbiddenPage />;
  }
  return <>{children}</>;
}
