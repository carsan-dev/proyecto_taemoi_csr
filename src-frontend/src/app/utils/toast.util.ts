import Swal from 'sweetalert2';

/**
 * Toast utility for non-intrusive notifications.
 * Use this for quick success messages that don't need user interaction.
 */
export const Toast = Swal.mixin({
  toast: true,
  position: 'top-end',
  showConfirmButton: false,
  timer: 3000,
  timerProgressBar: true,
  didOpen: (toast) => {
    toast.onmouseenter = Swal.stopTimer;
    toast.onmouseleave = Swal.resumeTimer;
  },
});

/**
 * Show a success toast notification.
 */
export function showSuccessToast(message: string): void {
  Toast.fire({
    icon: 'success',
    title: message,
  });
}

/**
 * Show an error toast notification.
 */
export function showErrorToast(message: string): void {
  Toast.fire({
    icon: 'error',
    title: message,
  });
}

/**
 * Show an info toast notification.
 */
export function showInfoToast(message: string): void {
  Toast.fire({
    icon: 'info',
    title: message,
  });
}

/**
 * Show a warning toast notification.
 */
export function showWarningToast(message: string): void {
  Toast.fire({
    icon: 'warning',
    title: message,
  });
}
