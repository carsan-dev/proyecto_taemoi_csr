import { HttpContextToken } from '@angular/common/http';

// The caller presents failures itself, including partial report batches.
export const ERROR_HANDLED_LOCALLY = new HttpContextToken<boolean>(() => false);
