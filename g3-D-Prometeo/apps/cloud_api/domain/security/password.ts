
import { randomBytes, scrypt, timingSafeEqual } from 'node:crypto';
import { Buffer } from 'node:buffer';

const SCRYPT_COST = 2 ** 17;
const SCRYPT_BLOCK_SIZE = 8;
const SCRYPT_PARALLELISM = 1;
const SCRYPT_KEY_LENGTH = 64;
const SCRYPT_MAX_MEMORY = 256 * 1024 * 1024;

export async function hashPassword(password: string): Promise<string> {
  const salt = randomBytes(16);
  const derivedKey = await derive(password, salt, SCRYPT_KEY_LENGTH, {
    cost: SCRYPT_COST,
    blockSize: SCRYPT_BLOCK_SIZE,
    parallelism: SCRYPT_PARALLELISM,
  });
  return ['scrypt', SCRYPT_COST, SCRYPT_BLOCK_SIZE, SCRYPT_PARALLELISM,
    Buffer.from(salt).toString('base64'),
    Buffer.from(derivedKey).toString('base64')].join('$');
}

export async function verifyPassword(password: string, encoded: string) {
  const [algorithm, cost, blockSize, parallelism, salt, expected] = encoded.split('$');
  if (algorithm !== 'scrypt' || !cost || !blockSize || !parallelism ||
      !salt || !expected) {
    return false;
  }
  const expectedKey = Buffer.from(expected, 'base64');
  try {
    const actual = await derive(password, Buffer.from(salt, 'base64'),
      expectedKey.length, {
        cost: Number(cost),
        blockSize: Number(blockSize),
        parallelism: Number(parallelism),
      });
    return actual.length === expectedKey.length &&
      timingSafeEqual(actual, expectedKey);
  } catch {
    return false;
  }
}

function derive(
  password: string,
  salt: Buffer,
  length: number,
  parameters: { cost: number; blockSize: number; parallelism: number },
) {
  return new Promise<Buffer>((resolve, reject) => {
    scrypt(password, salt, length, {
      N: parameters.cost,
      r: parameters.blockSize,
      p: parameters.parallelism,
      maxmem: SCRYPT_MAX_MEMORY,
    }, (error, key) => error ? reject(error) : resolve(key));
  });
}
