import { router, type Href } from 'expo-router';

// Clears the stack so the back gesture cannot return to a finished flow.
export function resetTo(href: Href) {
  if (router.canDismiss()) router.dismissAll();
  router.replace(href);
}
